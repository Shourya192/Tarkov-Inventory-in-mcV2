package com.tarkovinventory.container;

import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import com.tarkovinventory.capability.IPlayerEquipment;
import com.tarkovinventory.capability.ModCapabilities;
import com.tarkovinventory.client.screen.modules.EquipmentSlotType;
import com.tarkovinventory.inventory.EquipmentSlotTypeValidator;
import com.tarkovinventory.client.screen.modules.RightInventoryPanelRenderer;
import com.tarkovinventory.inventory.*;
import com.tarkovinventory.network.S2CEquipmentSyncPacket;
import com.tarkovinventory.network.S2CLootSyncPacket;
import com.tarkovinventory.network.S2CRigSyncPacket;
import com.tarkovinventory.network.ModNetwork;
import com.tarkovinventory.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Full Tarkov inventory menu.
 *
 * Because Slot.x and Slot.y are FINAL in this Forge build, loot-slot positions
 * must be baked in at construction time.  We solve this via a private record
 * {@link ConstructorArgs} that pre-computes everything (including loot item count)
 * BEFORE the constructor body runs.  The three public constructors each call a
 * static parser that returns a {@code ConstructorArgs}, and then delegate to the
 * single private constructor that does all the real work.
 */
public class TarkovInventoryMenu extends AbstractContainerMenu {

    // ── Slot range constants ─────────────────────────────────────────
    public static final int GRID_MAX            = GridInventory.MAX_CELLS;
    public static final int VANILLA_ARMOR_START = GRID_MAX;
    public static final int VANILLA_ARMOR_COUNT = 4;
    public static final int CUSTOM_EQUIP_START  = VANILLA_ARMOR_START + VANILLA_ARMOR_COUNT;
    public static final int CUSTOM_EQUIP_COUNT  = IPlayerEquipment.SLOT_COUNT;
    public static final int PLAYER_START        = CUSTOM_EQUIP_START + CUSTOM_EQUIP_COUNT;
    public static final int HOTBAR_START        = PLAYER_START + 27;
    public static final int RIG_START           = HOTBAR_START + 9;
    public static final int POCKETS_COUNT       = 7;

    // ── Loot / Corpse panel ──────────────────────────────────────────
    public  static final int LOOT_COLS     = RightInventoryPanelRenderer.COLS;
    public  static final int LOOT_ROWS     = RightInventoryPanelRenderer.LOOT_ROWS;
    /** Container-loot slots use [0..79]. Corpse uses [0..6]=pockets [7..150]=rig [151..294]=grid. */
    public  static final int LOOT_SLOTS    = 305;
    // Corpse loot layout:
    //   [0..7]   GEAR  (rig item, bp item, head, chest, legs, feet, face, ear)
    //   [8..14]  POCKETS (7 slots)
    //   [15..158] RIG contents grid (144 slots)
    //   [159..302] BACKPACK contents grid (144 slots)
    public  static final int CORPSE_GEAR_BASE    = 0;
    public  static final int CORPSE_GEAR_COUNT   = 10;  // 8 gear + PRIMARY + SECONDARY
    public  static final int CORPSE_POCKETS_BASE = 10;
    public  static final int CORPSE_POCKETS      = 7;
    public  static final int CORPSE_RIG_BASE     = 17;
    public  static final int CORPSE_GRID_BASE    = 161;
    private static final int LOOT_HEADER_H = RightInventoryPanelRenderer.HEADER_H;
    // ── Per-slot search system (Rummage / Tarkov style) ─────────────
    /** Bitmask entries (15 bits each) to track searched slots — covers 303 slots. */
    public  static final int BITMASK_ENTRIES = 21;
    // ContainerData layout: [0..20]=searched bitmask, [21]=currentSlot, [22]=progress, [23]=required

    // ── Layout constants ─────────────────────────────────────────────
    public static final int CELL        = 18;
    public static final int PANEL_GAP   = 20;
    public static final int EQUIP_W     = 190;
    public static final int RIGHT_W     = RightInventoryPanelRenderer.panelWidth() + 16;
    public static final int LABEL_H     = 14;
    public static final int SECTION_GAP = 8;

    private static final int EC1 = 10, EC2 = 86, EC3 = 162;

    // ── Pre-computed constructor args ────────────────────────────────
    private record ConstructorArgs(
            int rigCols, int rigRows, int bpCols, int bpRows,
            int lootCount, List<ItemStack> lootItems,
            @Nullable BlockPos lootPos, boolean isCorpse, String lootOwner,
            int corpseRigCols, int corpseRigRows,
            int corpseGridCols, int corpseGridRows) {}

    // ── Instance fields ──────────────────────────────────────────────
    private final EquipmentContainer equipContainer;
    private final IPlayerEquipment   cap;
    private final Inventory          playerInventory;
    private final RigContainer       rigContainer;
    private final RigContainer       backpackContainer;
    private int                      rigSlotCount;
    private int                      backpackSlotCount;

    private final SimpleContainer lootContainer = new SimpleContainer(LOOT_SLOTS) {
        @Override public void setChanged() { super.setChanged(); lootDirty = true; }
    };
    private boolean  lootDirty         = false;

    // ── Multiplayer: track all players viewing the same loot source ──
    // BlockPos → set of ServerPlayers who have that pos open right now.
    public static final java.util.concurrent.ConcurrentHashMap<BlockPos,
            java.util.Set<ServerPlayer>> LOOT_VIEWERS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Called client-side by S2CLootSyncPacket to apply a remote item change. */
    public void updateLootSlot(int idx, ItemStack stack) {
        lootContainer.setItem(idx, stack);
    }
    private int      lootSlotBase      = -1;
    public   BlockPos         activeLootPos     = null;
    public boolean activeLootIsCorpse = false;
    private String   activeLootOwner   = "";
    private int      activeLootCount   = 0;
    public int corpseRigCols = 3, corpseRigRows = 3;
    public int corpseGridCols = 6, corpseGridRows = 6;

    // ── Per-slot search fields ───────────────────────────────────────
    private final java.util.Set<Integer> searchedSlots  = new java.util.HashSet<>();
    private int currentSearchSlot   = -1;
    private int slotSearchProgress  = 0;
    private int slotSearchRequired  = 0;
    private int hoveredLootSlot     = -1;
    // ContainerData: [0..20]=searched bitmask, [21]=currentSlot, [22]=progress, [23]=required
    public  final net.minecraft.world.inventory.SimpleContainerData searchData =
            new net.minecraft.world.inventory.SimpleContainerData(BITMASK_ENTRIES + 3);

    private final int totalImageWidth, totalImageHeight;
    private final int centerPanelX, rightPanelX;
    private final int gridBaseY, rigGridY, pocketY, bpPanelY;
    private final int rigCols, rigRows, bpCols, bpRows;

    private int lastEquipChangeCount = -1;
    private int lastRigCols = 0, lastRigRows = 0;
    private int lastBpCols  = 0, lastBpRows  = 0;

    // ── THE single private constructor that does all the real work ───

    private TarkovInventoryMenu(int windowId, Inventory playerInv, ConstructorArgs a) {
        super(ModMenuTypes.TARKOV_INVENTORY.get(), windowId);

        this.playerInventory  = playerInv;
        this.cap              = ModCapabilities.get(playerInv.player)
                .orElseThrow(() -> new IllegalStateException("Missing Tarkov capability"));
        this.equipContainer   = new EquipmentContainer(playerInv.player, cap);
        this.rigContainer     = new RigContainer(playerInv.player, RigContainer.Mode.RIG);
        this.backpackContainer = new RigContainer(playerInv.player, RigContainer.Mode.BACKPACK);

        this.rigCols = a.rigCols() > 0 ? a.rigCols() : rigContainer.getCols();
        this.rigRows = a.rigRows() > 0 ? a.rigRows() : rigContainer.getRows();
        // 0 = not equipped (slots hidden). Only fall back to live if args > 0.
        this.bpCols  = a.bpCols()  > 0 ? a.bpCols()  : (backpackContainer.isItemEquipped() ? backpackContainer.getCols() : 0);
        this.bpRows  = a.bpRows()  > 0 ? a.bpRows()  : (backpackContainer.isItemEquipped() ? backpackContainer.getRows() : 0);

        // Loot state — must be set BEFORE createAllSlots() so slot positions are correct
        this.activeLootCount    = Math.min(a.lootCount(), LOOT_SLOTS);
        this.activeLootPos      = a.lootPos();
        this.activeLootIsCorpse = a.isCorpse();
        this.activeLootOwner    = a.lootOwner();
        this.corpseRigCols      = a.corpseRigCols();
        this.corpseRigRows      = a.corpseRigRows();
        this.corpseGridCols     = a.corpseGridCols();
        this.corpseGridRows     = a.corpseGridRows();

        // Layout
        int maxBpCols = Math.max(bpCols, com.tarkovinventory.inventory.BackpackSizes.DEFAULT_COLS);
        int centerW = Math.max(Math.max(maxBpCols, rigCols), 7) * CELL + 32;
        // Only include right panel width when a loot source is actually open
        int rightPanelW = (activeLootPos != null) ? PANEL_GAP + RIGHT_W : 0;
        this.totalImageWidth  = EQUIP_W + PANEL_GAP + centerW + rightPanelW;
        this.centerPanelX     = EQUIP_W + PANEL_GAP;
        this.rightPanelX      = centerPanelX + centerW + PANEL_GAP;
        this.pocketY          = 0;
        this.rigGridY         = CELL + SECTION_GAP + LABEL_H;
        // Reserve space for the ACTUAL equipped rig (or DEFAULT_ROWS if none), so the
        // backpack grid sits exactly below the rig grid. The renderer uses the same
        // value (live rigRows, falling back to DEFAULT_ROWS) so slots & cells align.
        int reservedRigRows   = rigRows > 0 ? rigRows : com.tarkovinventory.inventory.RigSizes.DEFAULT_ROWS;
        int maxBpRows = Math.max(bpRows, com.tarkovinventory.inventory.BackpackSizes.DEFAULT_ROWS);
        this.gridBaseY        = rigGridY + reservedRigRows * CELL + SECTION_GAP + LABEL_H;
        this.bpPanelY         = gridBaseY + maxBpRows * CELL + 30;
        int rightH = (activeLootPos != null) ? (activeLootIsCorpse
                ? RightInventoryPanelRenderer.corpseHeight(corpseRigRows, corpseGridRows)
                : RightInventoryPanelRenderer.panelHeight()) : 0;
        this.totalImageHeight = Math.max(Math.max(180, bpPanelY + 10), rightH) + 20;

        this.lastRigCols = rigContainer.isItemEquipped()      ? rigContainer.getCols()      : 0;
        this.lastRigRows = rigContainer.isItemEquipped()      ? rigContainer.getRows()      : 0;
        this.lastBpCols  = backpackContainer.isItemEquipped() ? backpackContainer.getCols() : 0;
        this.lastBpRows  = backpackContainer.isItemEquipped() ? backpackContainer.getRows() : 0;

        // Slots — loot slot positions are baked from activeLootCount (already set above)
        createAllSlots();

        // Populate loot container
        List<ItemStack> items = a.lootItems();
        for (int i = 0; i < items.size() && i < LOOT_SLOTS; i++) {
            lootContainer.setItem(i, items.get(i));
        }
        lootDirty = false;

        // CRITICAL: register searchData so per-slot search progress and the
        // searched-bitmask actually sync to the client. Without this the gold
        // border never advances and searched items never become visible.
        addDataSlots(searchData);
        // Initialise search header to "none" so client doesn't start stuck on slot 0
        searchData.set(BITMASK_ENTRIES, -1);

        // Restore THIS player's previously-searched slots so they don't have to
        // re-search every reopen. Per-player: other players' searches don't reveal
        // items for this player. Works for both corpses and regular containers.
        if (activeLootPos != null && playerInv.player instanceof ServerPlayer spSearch) {
            java.util.Set<Integer> restored;
            if (activeLootIsCorpse
                    && playerInv.player.level().getBlockEntity(activeLootPos)
                            instanceof TarkovCorpseBlockEntity beSearched) {
                restored = beSearched.getSearchedSlots(spSearch.getUUID());
            } else if (!activeLootIsCorpse && spSearch.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                restored = com.tarkovinventory.loot.ContainerSearchData.get(sl)
                        .getSearched(activeLootPos, spSearch.getUUID());
            } else {
                restored = java.util.Collections.emptySet();
            }
            for (int lootIdx : restored) {
                searchedSlots.add(lootIdx);
                int entry = lootIdx / 15, bit = lootIdx % 15;
                if (entry < BITMASK_ENTRIES)
                    searchData.set(entry, searchData.get(entry) | (1 << bit));
            }
        }

        // Register this player as a viewer of the loot source for multiplayer sync
        if (activeLootPos != null && playerInv.player instanceof ServerPlayer sp2) {
            LOOT_VIEWERS.computeIfAbsent(activeLootPos,
                    k -> java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>()))
                    .add(sp2);
        }

        // Server-side syncs
        if (playerInv.player instanceof ServerPlayer sp) {
            sendRigSync(sp, 0, rigContainer);
            sendRigSync(sp, 1, backpackContainer);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new S2CEquipmentSyncPacket(cap.serializeNBT()));
        }
    }

    // ── Public constructors ──────────────────────────────────────────

    /** Client-side (reads from network buffer). */
    public TarkovInventoryMenu(int windowId, Inventory playerInv, FriendlyByteBuf data) {
        this(windowId, playerInv, parseFromBuf(playerInv.player, data));
    }

    /** Server-side, no loot source. */
    public TarkovInventoryMenu(int windowId, Inventory playerInv, int ignored) {
        this(windowId, playerInv, parseFromBuf(playerInv.player, null));
    }

    /** Server-side, with a loot source (corpse or generic container). */
    public TarkovInventoryMenu(int windowId, Inventory playerInv, BlockPos lootPos, boolean isCorpse) {
        this(windowId, playerInv, parseFromWorld(playerInv.player, lootPos, isCorpse));
    }

    // ── Static parsers ───────────────────────────────────────────────

    private static ConstructorArgs parseFromBuf(Player player, @Nullable FriendlyByteBuf data) {
        if (data == null) {
            RigContainer rc = new RigContainer(player, RigContainer.Mode.RIG);
            RigContainer bp = new RigContainer(player, RigContainer.Mode.BACKPACK);
            return new ConstructorArgs(
                    rc.isItemEquipped() ? rc.getCols() : 0,
                    rc.isItemEquipped() ? rc.getRows() : 0,
                    bp.isItemEquipped() ? bp.getCols() : 0,
                    bp.isItemEquipped() ? bp.getRows() : 0,
                    0, List.of(), null, false, "", 3, 3, 6, 6);
        }
        data.readInt();
        int rc = data.readInt(), rr = data.readInt(), bc = data.readInt(), br = data.readInt();
        if (data.readBoolean()) {
            BlockPos pos  = data.readBlockPos();
            boolean corp  = data.readBoolean();
            String owner  = data.readUtf(64);
            int crc = data.readInt(), crr = data.readInt();
            int cgc = data.readInt(), cgr = data.readInt();
            int cnt       = data.readVarInt();
            List<ItemStack> items = new ArrayList<>(cnt);
            for (int i = 0; i < cnt; i++) items.add(data.readItem());
            return new ConstructorArgs(rc, rr, bc, br, cnt, items, pos, corp, owner, crc, crr, cgc, cgr);
        }
        return new ConstructorArgs(rc, rr, bc, br, 0, List.of(), null, false, "", 3, 3, 6, 6);
    }

    private static ConstructorArgs parseFromWorld(Player player, BlockPos lootPos, boolean isCorpse) {
        RigContainer rc = new RigContainer(player, RigContainer.Mode.RIG);
        RigContainer bp = new RigContainer(player, RigContainer.Mode.BACKPACK);
        int bpCols = bp.isItemEquipped() ? bp.getCols() : 0;
        int bpRows = bp.isItemEquipped() ? bp.getRows() : 0;

        Level level  = player.level();
        String owner = "";
        List<ItemStack> items = new ArrayList<>();
        int crc = 3, crr = 3, cgc = 6, cgr = 6;

        if (isCorpse && level.getBlockEntity(lootPos) instanceof TarkovCorpseBlockEntity be) {
            owner = be.getOwnerName();
            crc = be.getRigCols();  crr = be.getRigRows();
            cgc = be.getGridCols(); cgr = be.getGridRows();
            Map<String, ItemStack> sl = be.getSlottedItems();
            java.util.function.Function<String[], ItemStack> pick = keys -> {
                for (String k : keys) { ItemStack s = sl.get(k); if (s != null && !s.isEmpty()) return s.copy(); }
                return ItemStack.EMPTY;
            };
            // GEAR [0..9]
            ItemStack gRig  = pick.apply(new String[]{"cap.rig","curios.body","armor.chest"});
            ItemStack gBack = pick.apply(new String[]{"cap.back","curios.back"});
            ItemStack gHead = pick.apply(new String[]{"armor.head","curios.head"});
            ItemStack gCh   = pick.apply(new String[]{"armor.chest"});
            if (ItemStack.matches(gCh, gRig)) gCh = ItemStack.EMPTY;
            // Pack the corpse's rig/backpack contents into the gear item NBT so that
            // whenever the player grabs the rig/backpack, it already carries its items.
            // (Contents are stored MAX_COLS-indexed; convert to the item's tight layout.)
            if (!gRig.isEmpty())  gRig  = withPackedContents(gRig,  be.getRigContents(),  crc, crr);
            if (!gBack.isEmpty()) gBack = withPackedContents(gBack, be.getGridContents(), be.getGridCols(), be.getGridRows());
            for (ItemStack g : new ItemStack[]{gRig, gBack, gHead, gCh,
                    pick.apply(new String[]{"armor.legs"}), pick.apply(new String[]{"armor.feet"}),
                    pick.apply(new String[]{"cap.face","curios.facewear"}),
                    pick.apply(new String[]{"cap.ear","curios.earwear"}),
                    pick.apply(new String[]{"weapon.primary"}),
                    pick.apply(new String[]{"weapon.secondary"})})
                items.add(g);
            // POCKETS [10..16]
            for (ItemStack s : be.getPockets()) items.add(s.copy());
            // RIG CONTENTS [17..160] — stored MAX_COLS-indexed (consistent end-to-end)
            List<ItemStack> rc2 = be.getRigContents();
            for (int i = 0; i < GridInventory.MAX_CELLS; i++) {
                int col = i % GridInventory.MAX_COLS, row = i / GridInventory.MAX_COLS;
                boolean inBounds = col < crc && row < crr;
                items.add(inBounds && i < rc2.size() ? rc2.get(i).copy() : ItemStack.EMPTY);
            }
            // BACKPACK CONTENTS [161..304] — stored MAX_COLS-indexed
            List<ItemStack> gc = be.getGridContents();
            for (int i = 0; i < GridInventory.MAX_CELLS; i++)
                items.add(i < gc.size() ? gc.get(i).copy() : ItemStack.EMPTY);
        } else if (!isCorpse && level.getBlockEntity(lootPos) instanceof Container container) {
            owner = level.getBlockState(lootPos).getBlock().getName().getString();
            // Include ALL slots (even empty) so slot N in loot panel = slot N in container.
            // This preserves positions and allows depositing into empty slots.
            int size = Math.min(container.getContainerSize(), LOOT_COLS * LOOT_ROWS);
            for (int i = 0; i < size; i++) items.add(container.getItem(i).copy());
        }
        return new ConstructorArgs(rc.getCols(), rc.getRows(), bpCols, bpRows,
                items.size(), items, lootPos.immutable(), isCorpse, owner, crc, crr, cgc, cgr);
    }

    // ── writeDimensions ──────────────────────────────────────────────

    public static void writeDimensions(FriendlyByteBuf buf, Player player) {
        writeDimensions(buf, player, null, false);
    }

    public static void writeDimensions(FriendlyByteBuf buf, Player player,
                                        @Nullable BlockPos lootPos, boolean isCorpse) {
        buf.writeInt(0);
        RigContainer rcTmp = new RigContainer(player, RigContainer.Mode.RIG);
        RigContainer bpTmp = new RigContainer(player, RigContainer.Mode.BACKPACK);
        // Send 0 when not equipped so no slots land at visible positions in the new menu
        buf.writeInt(rcTmp.isItemEquipped() ? rcTmp.getCols() : 0);
        buf.writeInt(rcTmp.isItemEquipped() ? rcTmp.getRows() : 0);
        buf.writeInt(bpTmp.isItemEquipped() ? bpTmp.getCols() : 0);
        buf.writeInt(bpTmp.isItemEquipped() ? bpTmp.getRows() : 0);

        if (lootPos != null) {
            buf.writeBoolean(true);
            buf.writeBlockPos(lootPos);
            buf.writeBoolean(isCorpse);
            ConstructorArgs a = parseFromWorld(player, lootPos, isCorpse);
            buf.writeUtf(a.lootOwner(), 64);
            buf.writeInt(a.corpseRigCols());  buf.writeInt(a.corpseRigRows());
            buf.writeInt(a.corpseGridCols()); buf.writeInt(a.corpseGridRows());
            int count = Math.min(a.lootItems().size(), LOOT_SLOTS);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) buf.writeItem(a.lootItems().get(i));
        } else {
            buf.writeBoolean(false);
        }
    }

    // ── Slot creation ────────────────────────────────────────────────

    private void createAllSlots() {
        // 1. Backpack grid
        for (int i = 0; i < GRID_MAX; i++) {
            final int fi = i;
            int col = i % GridInventory.MAX_COLS, row = i / GridInventory.MAX_COLS;
            // Pre-allocate ALL slots at fixed positions — visibility controlled live by
            // isActive() and getItem(), exactly like rig slots. No menu refresh needed.
            GridAlignedContainer bpGridWrapper = new GridAlignedContainer(backpackContainer);
            addSlot(new Slot(bpGridWrapper, i,
                    centerPanelX + col * CELL,
                    gridBaseY    + row * CELL) {
                @Override public boolean isActive() {
                    return backpackContainer.isItemEquipped() && bpGridWrapper.inBounds(fi);
                }
                @Override public ItemStack getItem() {
                    return backpackContainer.isItemEquipped() ? super.getItem() : ItemStack.EMPTY;
                }
                @Override public boolean mayPlace(@NotNull ItemStack s) {
                    return backpackContainer.isItemEquipped() && bpGridWrapper.inBounds(fi);
                }
                @Override public boolean mayPickup(@NotNull Player p) {
                    return backpackContainer.isItemEquipped() && bpGridWrapper.inBounds(fi);
                }
            });
        }
        // 2. Vanilla armor
        int[] armorIdx = {39, 38, 37, 36};
        int[] armorX   = {EC3, EC3, EC3, EC2};
        int[] armorY   = {0, 20, 40, 80};
        EquipmentSlotType[] armorTypes = {
                EquipmentSlotType.HEAD, EquipmentSlotType.ARMOR,
                EquipmentSlotType.PANTS, EquipmentSlotType.BOOTS};
        for (int i = 0; i < 4; i++)
            addSlot(new TarkovSlot(playerInventory, armorIdx[i], armorX[i], armorY[i], armorTypes[i]));

        // 3. Custom equipment
        int[] custX = {EC1, EC1, EC1, -2000, EC2, -2000, EC1};
        int[] custY = {0, 20, 40, -2000, 60, -2000, 100};
        EquipmentSlotType[] custT = {
                EquipmentSlotType.FACE, EquipmentSlotType.EAR, EquipmentSlotType.RIG,
                EquipmentSlotType.PANTS, EquipmentSlotType.KNEE,
                EquipmentSlotType.UNKNOWN, EquipmentSlotType.BACKPACK};
        for (int i = 0; i < CUSTOM_EQUIP_COUNT; i++)
            addSlot(new TarkovSlot(equipContainer, 4 + i, custX[i], custY[i], custT[i]));

        // 4. Player main inventory — off-screen
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, -2000, -2000) {
                    @Override public boolean isActive() { return false; }
                });

        // 5. Hotbar (PRIMARY / SECONDARY / pockets)
        addSlot(new Slot(playerInventory, 0, EC1, 140));
        addSlot(new Slot(playerInventory, 1, EC1, 160));
        for (int i = 0; i < POCKETS_COUNT; i++)
            addSlot(new Slot(playerInventory, i + 2, centerPanelX + i * CELL, pocketY));

        // 6. Rig — pre-allocate MAX_CELLS slots at fixed positions.
        //    isActive() / mayPlace() read the live equipped state each tick,
        //    so equip/unequip is instant with zero screen flicker.
        GridAlignedContainer rigWrapper = new GridAlignedContainer(rigContainer);
        this.rigSlotCount = GridInventory.MAX_CELLS;
        for (int i = 0; i < GridInventory.MAX_CELLS; i++) {
            final int fi = i;
            int col = i % GridInventory.MAX_COLS;
            int row = i / GridInventory.MAX_COLS;
            addSlot(new TarkovSlot(rigWrapper, i,
                    centerPanelX + col * CELL,
                    rigGridY     + row * CELL,
                    EquipmentSlotType.UNKNOWN) {
                @Override public boolean isActive()                     { return rigContainer.isItemEquipped() && rigWrapper.inBounds(fi); }
                @Override public ItemStack getItem() {
                    // Hide items when rig not equipped
                    return rigContainer.isItemEquipped() ? super.getItem() : ItemStack.EMPTY;
                }
                @Override public boolean mayPlace(@NotNull ItemStack s) { return rigContainer.isItemEquipped() && rigWrapper.inBounds(fi); }
            });
        }

        // 7. Backpack hidden duplicates — section 1 now handles the backpack display.
        //    These slots must still exist to preserve slot index layout for sections 8+.
        GridAlignedContainer bpDummyWrapper = new GridAlignedContainer(backpackContainer);
        this.backpackSlotCount = GridInventory.MAX_CELLS;
        for (int i = 0; i < GridInventory.MAX_CELLS; i++) {
            addSlot(new Slot(bpDummyWrapper, i, -2000, -2000) {
                @Override public boolean isActive()                    { return false; }
                @Override public boolean mayPlace(@NotNull ItemStack s){ return false; }
                @Override public boolean mayPickup(@NotNull Player p)  { return false; }
            });
        }

        // 8. Right-panel slots — 295 pre-allocated.
        //    Container: slots [0..79] flat grid; [80..294] hidden.
        //    Corpse:    [0..7] GEAR row, [8..14] POCKETS row, rest hidden.
        this.lootSlotBase = slots.size();
        // These MUST match RightInventoryPanelRenderer's corpse layout exactly,
        // otherwise slots and drawn cells diverge (phantom boxes).
        int gearY         = LOOT_HEADER_H + 4;                          // renderer: lootGridTop+4
        int weapY         = gearY + CELL + 14;                          // renderer WEAPONS row
        int pocketRowY    = weapY + CELL + 16 + LABEL_H - 2;            // renderer pocketCellY
        int cRigY         = pocketRowY + CELL + 16 + LABEL_H - 2;       // renderer rigCellY
        int cGridY        = cRigY + corpseRigRows * CELL + 16 + LABEL_H - 2; // renderer bpCellY

        for (int i = 0; i < LOOT_SLOTS; i++) {
            int sx, sy;
            if (activeLootIsCorpse) {
                if (i < 8) {
                    // GEAR row (first 8 gear items)
                    sx = rightPanelX + i * CELL;
                    sy = gearY;
                } else if (i < CORPSE_GEAR_COUNT) {
                    // WEAPONS row (PRIMARY = 8, SECONDARY = 9)
                    sx = rightPanelX + (i - 8) * CELL;
                    sy = weapY;
                } else if (i >= CORPSE_POCKETS_BASE && i < CORPSE_POCKETS_BASE + CORPSE_POCKETS) {
                    // POCKETS row
                    sx = rightPanelX + (i - CORPSE_POCKETS_BASE) * CELL;
                    sy = pocketRowY;
                } else if (i >= CORPSE_RIG_BASE && i < CORPSE_GRID_BASE) {
                    // RIG contents grid — only active if rig item (gear slot 0) is present
                    int ri  = i - CORPSE_RIG_BASE;
                    int col = ri % GridInventory.MAX_COLS;
                    int row = ri / GridInventory.MAX_COLS;
                    boolean inBounds = col < corpseRigCols && row < corpseRigRows;
                    sx = inBounds ? rightPanelX + col * CELL : -2000;
                    sy = inBounds ? cRigY       + row * CELL : -2000;
                } else if (i >= CORPSE_GRID_BASE) {
                    // BACKPACK contents grid — only active if backpack item (gear slot 1) is present
                    int gi  = i - CORPSE_GRID_BASE;
                    int col = gi % GridInventory.MAX_COLS;
                    int row = gi / GridInventory.MAX_COLS;
                    boolean inBounds = col < corpseGridCols && row < corpseGridRows;
                    sx = inBounds ? rightPanelX + col * CELL : -2000;
                    sy = inBounds ? cGridY      + row * CELL : -2000;
                } else {
                    sx = -2000; sy = -2000;
                }
            } else {
                // Flat container grid: first N slots visible
                boolean active = i < activeLootCount && i < LOOT_COLS * LOOT_ROWS;
                sx = active ? rightPanelX + (i % LOOT_COLS) * CELL : -2000;
                sy = active ? LOOT_HEADER_H + (i / LOOT_COLS) * CELL : -2000;
            }
            final int fsx = sx, fsy = sy;
            final boolean isCorpseSlot = activeLootIsCorpse;
            final boolean isRigContent  = activeLootIsCorpse && i >= CORPSE_RIG_BASE  && i < CORPSE_GRID_BASE;
            final boolean isBpContent   = activeLootIsCorpse && i >= CORPSE_GRID_BASE;
            final int lootIdx = i;
            addSlot(new Slot(lootContainer, i, sx, sy) {
                @Override public boolean isActive() {
                    if (fsx == -2000) return false;
                    if (isRigContent && lootContainer.getItem(CORPSE_GEAR_BASE + 0).isEmpty()) return false;
                    if (isBpContent  && lootContainer.getItem(CORPSE_GEAR_BASE + 1).isEmpty()) return false;
                    return true;
                }
                @Override public ItemStack getItem() {
                    // Hide items until slot is searched — MC renders items regardless of isActive(),
                    // so we must return EMPTY to truly hide them before they're revealed.
                    ItemStack real = super.getItem();
                    if (real.isEmpty()) return real;
                    return isSlotSearchedClient(lootIdx) ? real : ItemStack.EMPTY;
                }
                @Override public boolean mayPlace(@NotNull ItemStack s) {
                    if (fsx == -2000) return false;
                    if (isCorpseSlot) {
                        if (!isActive()) return false;
                        // Enforce gear-slot types: only the right item kind per gear slot.
                        if (lootIdx < CORPSE_GEAR_COUNT) {
                            return corpseGearSlotAccepts(lootIdx, s);
                        }
                        return true; // pockets / rig-content / backpack-content accept anything
                    }
                    return true;
                }
                @Override public boolean mayPickup(@NotNull Player p) {
                    if (!isActive()) return false;
                    return isSlotSearchedClient(lootIdx);
                }
            });
        }
    }

    // ── Sync & writeback ─────────────────────────────────────────────

    private void sendRigSync(ServerPlayer player, int mode, RigContainer container) {
        CompoundTag data = container.getRigInventory() != null
                ? container.getRigInventory().serializeNBT() : new CompoundTag();
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CRigSyncPacket(mode, data, container.getCols(), container.getRows()));
    }

    public void syncRigToClient(int mode) {
        if (playerInventory.player instanceof ServerPlayer sp) {
            RigContainer c = mode == 0 ? rigContainer : backpackContainer;
            c.reloadFromItem();
            sendRigSync(sp, mode, c);
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (playerInventory.player instanceof ServerPlayer sp) {

            // When equipment changes: reload containers, sync capability, sync to Curios
            int cc = equipContainer.getChangeCount();
            if (cc != lastEquipChangeCount) {
                lastEquipChangeCount = cc;
                rigContainer.reloadFromItem();
                backpackContainer.reloadFromItem();
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new S2CEquipmentSyncPacket(cap.serializeNBT()));
                // Sync custom slots to Curios for visual purposes
                if (com.tarkovinventory.compat.CuriosCompat.isLoaded()) {
                    syncToCurios(sp, "facewear", cap.getSlot(IPlayerEquipment.SLOT_FACE));
                    syncToCurios(sp, "earwear",  cap.getSlot(IPlayerEquipment.SLOT_EARPIECE));
                    syncToCurios(sp, "knees",    cap.getSlot(IPlayerEquipment.SLOT_KNEES));
                    syncToCurios(sp, "body",     cap.getSlot(IPlayerEquipment.SLOT_RIG));
                    // For "back": always mirror our capability state to Curios (both equip AND unequip).
                    // This prevents Curios from pushing stale data back into cap.SLOT_ON_BACK.
                    ItemStack ourBack = cap.getSlot(IPlayerEquipment.SLOT_ON_BACK);
                    ItemStack curiosBack = com.tarkovinventory.compat.CuriosCompat.getSlotItem(sp, "back", 0);
                    if (!ItemStack.matches(ourBack, curiosBack)) {
                        com.tarkovinventory.compat.CuriosCompat.setSlot(sp, "back", 0, ourBack);
                    }
                }
            }

            // Per-slot search tick — runs EVERY tick (not just on equipment change)
            if (hasActiveLootSource()) tickLootSearch(sp);

            int rigColsNow = rigContainer.isItemEquipped()      ? rigContainer.getCols()      : 0;
            int rigRowsNow = rigContainer.isItemEquipped()      ? rigContainer.getRows()      : 0;
            int bpColsNow  = backpackContainer.isItemEquipped() ? backpackContainer.getCols() : 0;
            int bpRowsNow  = backpackContainer.isItemEquipped() ? backpackContainer.getRows() : 0;
            // Refresh ONLY when new slots need to appear (equipping or size change).
            // IMPORTANT: compare against the PREVIOUS values BEFORE updating them,
            // otherwise newRig/newBp are always false and the equip never refreshes.
            boolean newRig = rigColsNow > 0 && lastRigCols == 0;
            boolean newBp  = bpColsNow  > 0 && lastBpCols  == 0;
            boolean resized = (rigColsNow > 0 && rigColsNow != lastRigCols)
                           || (bpColsNow  > 0 && bpColsNow  != lastBpCols)
                           || (rigRowsNow > 0 && rigRowsNow != lastRigRows)
                           || (bpRowsNow  > 0 && bpRowsNow  != lastBpRows);
            // Now store current values for next tick's comparison.
            lastRigCols = rigColsNow; lastRigRows = rigRowsNow;
            lastBpCols  = bpColsNow;  lastBpRows  = bpRowsNow;
            // Unequipping is handled purely visually: renderer skips cells when !equipped,
            // and isActive()=false blocks all interaction. No menu close/reopen needed,
            // so the cursor item stays on the cursor.
            if (newRig || newBp || resized) scheduleMenuRefresh(sp);

            // Write loot changes back to source
            if (lootDirty && activeLootPos != null) {
                lootDirty = false;
                writeLootBack(sp);
                broadcastLootToOtherViewers(sp);
            }
        }
    }

    /**
     * Closes the current menu and reopens it on the next server tick so that
     * updated rig/backpack equipped state is baked into the new slot positions.
     *
     * Any item on the player's cursor is returned to inventory first so nothing
     * is lost or duplicated.
     */
    private void scheduleMenuRefresh(ServerPlayer sp) {
        final BlockPos lp  = activeLootPos;
        final boolean  ic  = activeLootIsCorpse;
        final AbstractContainerMenu self = this;
        // Persist any loot changes NOW (synchronously) before the menu is rebuilt.
        // Taking the rig/backpack into an equip slot changes equipContainer, not
        // lootContainer, so lootDirty may be false — but the corpse still needs to
        // be updated (gear item removed, contents cleared) or it will re-spawn the
        // looted rig/backpack when parseFromWorld re-reads it on reopen.
        if (lp != null) {
            lootDirty = false;
            writeLootBack(sp);
        }
        sp.server.execute(() -> {
            if (sp.containerMenu != self) return;

            // Take any cursor item and place it in pockets (hotbar 2-8).
            // We never try to restore to cursor — packet ordering makes that
            // unreliable and causes items to land in PRIMARY/SECONDARY slot.
            ItemStack cursor = sp.containerMenu.getCarried().copy();
            sp.containerMenu.setCarried(ItemStack.EMPTY); // clear before close so removed() skips it

            if (!cursor.isEmpty()) {
                boolean placed = false;
                for (int i = 2; i <= 8 && !placed; i++) {
                    if (sp.getInventory().getItem(i).isEmpty()) {
                        sp.getInventory().setItem(i, cursor.copy());
                        placed = true;
                    }
                }
                if (!placed) sp.drop(cursor, false);
            }

            sp.closeContainer();

            if (lp != null) {
                net.minecraftforge.network.NetworkHooks.openScreen(sp,
                        new LootMenuProvider(lp, ic),
                        buf -> writeDimensions(buf, sp, lp, ic));
            } else {
                net.minecraftforge.network.NetworkHooks.openScreen(sp,
                        new net.minecraft.world.SimpleMenuProvider(
                                (id, inv, p) -> new TarkovInventoryMenu(id, inv, 0),
                                net.minecraft.network.chat.Component.literal("Tarkov Inventory")),
                        buf -> writeDimensions(buf, sp));
            }
        });
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        // Deregister from viewer list
        if (activeLootPos != null && player instanceof ServerPlayer sp) {
            java.util.Set<ServerPlayer> viewers = LOOT_VIEWERS.get(activeLootPos);
            if (viewers != null) {
                viewers.remove(sp);
                if (viewers.isEmpty()) LOOT_VIEWERS.remove(activeLootPos);
            }
        }
        if (player instanceof ServerPlayer sp && lootDirty && activeLootPos != null) {
            lootDirty = false;
            writeLootBack(sp);
            broadcastLootToOtherViewers(sp);
        }
    }

    /** Sends the current loot container state to all OTHER players viewing the same source. */
    private void broadcastLootToOtherViewers(ServerPlayer self) {
        if (activeLootPos == null) return;
        java.util.Set<ServerPlayer> viewers = LOOT_VIEWERS.get(activeLootPos);
        if (viewers == null || viewers.size() <= 1) return;
        // Build item list from current loot container
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < LOOT_SLOTS; i++) items.add(lootContainer.getItem(i).copy());
        S2CLootSyncPacket pkt = new S2CLootSyncPacket(activeLootPos, items);
        for (ServerPlayer viewer : viewers) {
            if (viewer != self) {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), pkt);
            }
        }
    }

    /** Returns a copy of the gear item with the given MAX_COLS-indexed contents
     *  packed into its "TarkovRigInventory" NBT (converted to tight cols×rows). */
    private static ItemStack withPackedContents(ItemStack item, List<ItemStack> maxColsItems, int cols, int rows) {
        cols = Math.max(1, cols); rows = Math.max(1, rows);
        com.tarkovinventory.inventory.RigInventory inv =
                new com.tarkovinventory.inventory.RigInventory(cols, rows);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int maxIdx = row * GridInventory.MAX_COLS + col;
                if (maxIdx < maxColsItems.size()) {
                    ItemStack s = maxColsItems.get(maxIdx);
                    if (!s.isEmpty()) inv.setItem(row * cols + col, s.copy());
                }
            }
        }
        ItemStack copy = item.copy();
        copy.getOrCreateTag().put("TarkovRigInventory", inv.serializeNBT());
        return copy;
    }

    /** Returns the first non-empty stack among the given keys in the map, or EMPTY. */
    /** Reads a gear item's "TarkovRigInventory" NBT (tight cols×rows layout) and
     *  returns a MAX_COLS-indexed flat list matching the corpse content storage. */
    private static List<ItemStack> unpackContentsFromItem(ItemStack item, int cols, int rows) {
        ItemStack[] out = new ItemStack[GridInventory.MAX_CELLS];
        java.util.Arrays.fill(out, ItemStack.EMPTY);
        cols = Math.max(1, cols); rows = Math.max(1, rows);
        if (!item.isEmpty() && item.getTag() != null && item.getTag().contains("TarkovRigInventory")) {
            com.tarkovinventory.inventory.RigInventory inv =
                    com.tarkovinventory.inventory.RigInventory.unwrapFromNBT(
                            item.getTag().getCompound("TarkovRigInventory"));
            int invSize = inv.getCols() * inv.getRows();
            for (int row = 0; row < rows; row++) {
                for (int col = 0; col < cols; col++) {
                    int tight = row * cols + col;
                    int maxIdx = row * GridInventory.MAX_COLS + col;
                    if (tight < invSize && maxIdx < out.length) {
                        out[maxIdx] = inv.getItem(tight).copy();
                    }
                }
            }
        }
        return new ArrayList<>(java.util.Arrays.asList(out));
    }

    private static ItemStack firstNonEmpty(Map<String, ItemStack> map, String[] keys) {
        for (String k : keys) {
            ItemStack s = map.get(k);
            if (s != null && !s.isEmpty()) return s;
        }
        return ItemStack.EMPTY;
    }

    /**
     * Packs the MAX_COLS-indexed display contents into the gear item's NBT
     * ("TarkovRigInventory"), converting to the item's tight cols×rows layout so
     * RigContainer reads them back at the correct positions when equipped.
     */
    private static void packContentsIntoItem(Map<String, ItemStack> sl, String[] keys,
            List<ItemStack> maxColsItems, int cols, int rows) {
        String key = null;
        for (String k : keys) { if (sl.containsKey(k) && !sl.get(k).isEmpty()) { key = k; break; } }
        if (key == null) return;
        ItemStack gearItem = sl.get(key);
        cols = Math.max(1, cols); rows = Math.max(1, rows);
        com.tarkovinventory.inventory.RigInventory inv =
                new com.tarkovinventory.inventory.RigInventory(cols, rows);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int maxIdx = row * GridInventory.MAX_COLS + col;
                if (maxIdx < maxColsItems.size()) {
                    ItemStack s = maxColsItems.get(maxIdx);
                    if (!s.isEmpty()) inv.setItem(row * cols + col, s.copy());
                }
            }
        }
        ItemStack copy = gearItem.copy();
        copy.getOrCreateTag().put("TarkovRigInventory", inv.serializeNBT());
        sl.put(key, copy);
    }

    /** Validates that an item is allowed in a given corpse GEAR slot index.
     *  Gear layout: 0=rig 1=backpack 2=head 3=chest 4=legs 5=feet 6=face 7=ear
     *  8=primary 9=secondary. Empty stacks (taking out) always allowed. */
    private static boolean corpseGearSlotAccepts(int gearIdx, ItemStack s) {
        if (s.isEmpty()) return true;
        EquipmentSlotType type = switch (gearIdx) {
            case 0 -> EquipmentSlotType.RIG;
            case 1 -> EquipmentSlotType.BACKPACK;
            case 2 -> EquipmentSlotType.HEAD;
            case 3 -> EquipmentSlotType.ARMOR;
            case 4 -> EquipmentSlotType.PANTS;
            case 5 -> EquipmentSlotType.BOOTS;
            case 6 -> EquipmentSlotType.FACE;
            case 7 -> EquipmentSlotType.EAR;
            case 8, 9 -> EquipmentSlotType.UNKNOWN; // weapons — accept any item
            default -> EquipmentSlotType.UNKNOWN;
        };
        if (type == EquipmentSlotType.UNKNOWN) return true;
        return EquipmentSlotTypeValidator.isValid(s, type);
    }

    private void writeLootBack(ServerPlayer sp) {
        Level level = sp.level();
        if (activeLootIsCorpse) {
            if (!(level.getBlockEntity(activeLootPos) instanceof TarkovCorpseBlockEntity be)) return;
            // GEAR [0..9]
            String[][] gearKeys = {
                {"cap.rig","curios.body"}, {"cap.back","curios.back"},
                {"armor.head"}, {"armor.chest"}, {"armor.legs"}, {"armor.feet"},
                {"cap.face","curios.facewear"}, {"cap.ear","curios.earwear"},
                {"weapon.primary"}, {"weapon.secondary"}
            };
            Map<String, ItemStack> prevSl = new java.util.LinkedHashMap<>(be.getSlottedItems());
            Map<String, ItemStack> sl = new java.util.LinkedHashMap<>(prevSl);
            for (int i = 0; i < CORPSE_GEAR_COUNT; i++) {
                ItemStack updated = lootContainer.getItem(CORPSE_GEAR_BASE + i);
                for (String k : gearKeys[i]) { if (sl.containsKey(k)) { sl.put(k, updated.copy()); break; } }
                sl.put(gearKeys[i][0], updated.copy());
            }

            // Gather current grid contents (MAX_COLS-indexed display layout)
            List<ItemStack> rigItems = new ArrayList<>();
            for (int i = 0; i < GridInventory.MAX_CELLS; i++) rigItems.add(lootContainer.getItem(CORPSE_RIG_BASE + i).copy());
            List<ItemStack> gridItems = new ArrayList<>();
            for (int i = 0; i < GridInventory.MAX_CELLS; i++) gridItems.add(lootContainer.getItem(CORPSE_GRID_BASE + i).copy());

            // RIG ITEM handling:
            //  • newly PLACED (absent before, present now): unpack the item's own NBT
            //    contents into the corpse so they show and aren't wiped.
            //  • still present: keep the display contents packed into the item NBT.
            //  • taken: clear the corpse's loose rig contents.
            boolean rigPresent  = !firstNonEmpty(sl, gearKeys[0]).isEmpty();
            boolean rigWasThere = !firstNonEmpty(prevSl, gearKeys[0]).isEmpty();
            if (rigPresent && !rigWasThere) {
                // Just placed → pull contents out of the item's NBT into the corpse,
                // using the placed rig's own registered dimensions.
                ItemStack placedRig = firstNonEmpty(sl, gearKeys[0]);
                int pc = com.tarkovinventory.inventory.RigSizes.getCols(placedRig);
                int pr = com.tarkovinventory.inventory.RigSizes.getRows(placedRig);
                List<ItemStack> unpacked = unpackContentsFromItem(placedRig, pc, pr);
                be.setRigContents(unpacked, pc, pr);
            } else if (rigPresent) {
                packContentsIntoItem(sl, gearKeys[0], rigItems, corpseRigCols, corpseRigRows);
                be.setRigContents(rigItems, corpseRigCols, corpseRigRows);
            } else {
                be.setRigContents(new ArrayList<>(), corpseRigCols, corpseRigRows);
            }

            boolean bpPresent  = !firstNonEmpty(sl, gearKeys[1]).isEmpty();
            boolean bpWasThere = !firstNonEmpty(prevSl, gearKeys[1]).isEmpty();
            if (bpPresent && !bpWasThere) {
                ItemStack placedBp = firstNonEmpty(sl, gearKeys[1]);
                int pc = com.tarkovinventory.inventory.BackpackSizes.getCols(placedBp);
                int pr = com.tarkovinventory.inventory.BackpackSizes.getRows(placedBp);
                List<ItemStack> unpacked = unpackContentsFromItem(placedBp, pc, pr);
                be.setGridContents(unpacked, pc, pr);
            } else if (bpPresent) {
                packContentsIntoItem(sl, gearKeys[1], gridItems, corpseGridCols, corpseGridRows);
                be.setGridContents(gridItems, corpseGridCols, corpseGridRows);
            } else {
                be.setGridContents(new ArrayList<>(), corpseGridCols, corpseGridRows);
            }

            sl.entrySet().removeIf(e -> e.getValue().isEmpty());
            be.setSlottedItems(sl);
            // POCKETS [10..16]
            for (int i = 0; i < CORPSE_POCKETS; i++) be.setPocketItem(i, lootContainer.getItem(CORPSE_POCKETS_BASE + i));
            // Corpse stays in the world even when emptied (persistent looted corpse).
        } else {
            if (!(level.getBlockEntity(activeLootPos) instanceof Container container)) return;
            for (int i = 0; i < Math.min(LOOT_SLOTS, container.getContainerSize()); i++)
                container.setItem(i, i < LOOT_SLOTS ? lootContainer.getItem(i).copy() : ItemStack.EMPTY);
        }
    }

    // ── GridAlignedContainer ─────────────────────────────────────────
    /**
     * Wraps a {@link RigContainer} and always presents {@link GridInventory#MAX_CELLS}
     * slots arranged in a {@link GridInventory#MAX_COLS}-wide grid.
     *
     * Maps UI slot index (MAX_COLS-based) → rig slot index (rigCols-based) so that
     * Tarkov slots at fixed screen positions correctly address the underlying rig/backpack.
     * Out-of-bounds slots (beyond the equipped item's dimensions) return EMPTY and
     * reject writes, and their isActive() / mayPlace() return false.
     */
    private static final class GridAlignedContainer implements Container {
        private final RigContainer rig;
        GridAlignedContainer(RigContainer rig) { this.rig = rig; }

        @Override public int  getContainerSize()                  { return GridInventory.MAX_CELLS; }
        @Override public boolean isEmpty()                         { return rig.isEmpty(); }
        @Override public void setChanged()                         { rig.setChanged(); }
        @Override public boolean stillValid(@NotNull Player p)     { return rig.stillValid(p); }
        @Override public void clearContent()                       { rig.clearContent(); }

        @Override public ItemStack getItem(int ui) {
            int rs = toRig(ui); return rs >= 0 ? rig.getItem(rs) : ItemStack.EMPTY;
        }
        @Override public ItemStack removeItem(int ui, int n) {
            int rs = toRig(ui); return rs >= 0 ? rig.removeItem(rs, n) : ItemStack.EMPTY;
        }
        @Override public ItemStack removeItemNoUpdate(int ui) {
            int rs = toRig(ui); return rs >= 0 ? rig.removeItemNoUpdate(rs) : ItemStack.EMPTY;
        }
        @Override public void setItem(int ui, @NotNull ItemStack s) {
            int rs = toRig(ui); if (rs >= 0) rig.setItem(rs, s);
        }

        /** Returns the rig slot index for a MAX_COLS-based UI slot, or -1 if out of bounds. */
        int toRig(int ui) {
            int cols = rig.getCols(), rows = rig.getRows();
            int c = ui % GridInventory.MAX_COLS, r = ui / GridInventory.MAX_COLS;
            if (c >= cols || r >= rows) return -1;
            return r * cols + c;
        }
        boolean inBounds(int ui) { return toRig(ui) >= 0; }
    }

    // ── LootMenuProvider ─────────────────────────────────────────────

    public static final class LootMenuProvider implements MenuProvider {
        private final BlockPos pos;
        private final boolean  isCorpse;
        public LootMenuProvider(BlockPos pos, boolean isCorpse) { this.pos = pos; this.isCorpse = isCorpse; }
        @Override public @NotNull Component getDisplayName() { return Component.literal("Tarkov Inventory"); }
        @Override public @NotNull AbstractContainerMenu createMenu(int id, @NotNull Inventory inv, @NotNull Player p) {
            return new TarkovInventoryMenu(id, inv, pos, isCorpse);
        }
    }

    // ── Shift-click ──────────────────────────────────────────────────

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        int rigEnd  = RIG_START  + GridInventory.MAX_CELLS;
        int bpEnd   = rigEnd     + GridInventory.MAX_CELLS;
        int custEnd = CUSTOM_EQUIP_START + CUSTOM_EQUIP_COUNT;

        if (index < GRID_MAX) {
            // Backpack grid → pockets
            if (!moveItemStackTo(stack, HOTBAR_START + 2, RIG_START, false))
                return ItemStack.EMPTY;
        } else if (index >= VANILLA_ARMOR_START && index < VANILLA_ARMOR_START + VANILLA_ARMOR_COUNT) {
            // Armor → pockets
            if (!moveItemStackTo(stack, HOTBAR_START + 2, RIG_START, false)) return ItemStack.EMPTY;
        } else if (index >= CUSTOM_EQUIP_START && index < custEnd) {
            // RIG / BACKPACK / FACE / EAR / KNEES → pockets
            if (!moveItemStackTo(stack, HOTBAR_START + 2, RIG_START, false)) return ItemStack.EMPTY;
        } else if (index >= HOTBAR_START + 2 && index < RIG_START) {
            // Pockets → chest deposit if open, else rig grid → backpack grid
            if (hasActiveLootSource() && !activeLootIsCorpse) {
                if (!moveItemStackTo(stack, lootSlotBase,
                        lootSlotBase + Math.min(activeLootCount, LOOT_COLS * LOOT_ROWS), false))
                    return ItemStack.EMPTY;
            } else {
                if (!moveItemStackTo(stack, RIG_START, rigEnd, false))
                    if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                        return ItemStack.EMPTY;
            }
        } else if (index >= RIG_START && index < rigEnd) {
            // Rig grid → pockets → backpack grid
            if (!moveItemStackTo(stack, HOTBAR_START + 2, RIG_START, false))
                if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                    return ItemStack.EMPTY;
        } else if (index >= rigEnd && index < bpEnd) {
            // Hidden section 7 → pockets
            if (!moveItemStackTo(stack, HOTBAR_START + 2, RIG_START, false)) return ItemStack.EMPTY;
        } else if (lootSlotBase >= 0 && index >= lootSlotBase && index < lootSlotBase + LOOT_SLOTS) {
            // Loot → pockets → rig grid → backpack grid
            if (!moveItemStackTo(stack, HOTBAR_START + 2, RIG_START, false))
                if (!moveItemStackTo(stack, RIG_START, rigEnd, false))
                    if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                        return ItemStack.EMPTY;
        }
                if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }
    @Override public boolean stillValid(@NotNull Player player) { return true; }

    // ── Accessors ────────────────────────────────────────────────────

    public EquipmentContainer getEquipContainer() { return equipContainer; }
    public RigContainer     getRigContainer()     { return rigContainer; }
    public RigContainer     getBackpackContainer() { return backpackContainer; }
    public IPlayerEquipment getCapability()       { return cap; }
    public SimpleContainer  getLootContainer()    { return lootContainer; }

    private static void syncToCurios(ServerPlayer player, String slotId, ItemStack stack) {
        // Never clear Curios slots externally — some Curios versions return the item
        // to the player when a slot is cleared from outside, causing duplication.
        // We only mirror EQUIP operations; unequip is handled by our own capability.
        if (stack.isEmpty()) return;
        ItemStack current = com.tarkovinventory.compat.CuriosCompat.getSlotItem(player, slotId, 0);
        if (!ItemStack.matches(current, stack))
            com.tarkovinventory.compat.CuriosCompat.setSlot(player, slotId, 0, stack);
    }

    public boolean hasActiveLootSource() { return activeLootPos != null; }
    /** True if the given loot-container slot index has been fully searched. Works on both sides. */
    public boolean isSlotSearchedClient(int lootIdx) {
        int entry = lootIdx / 15, bit = lootIdx % 15;
        return entry < BITMASK_ENTRIES && (searchData.get(entry) & (1 << bit)) != 0;
    }
    private boolean isSlotSearchedServer(int lootIdx) { return searchedSlots.contains(lootIdx); }

    private void markSlotSearched(int lootIdx) {
        searchedSlots.add(lootIdx);
        int entry = lootIdx / 15, bit = lootIdx % 15;
        if (entry < BITMASK_ENTRIES)
            searchData.set(entry, searchData.get(entry) | (1 << bit));
        // Persist (per-player) so searched state survives menu reopen — for both
        // corpses (stored on the block entity) and containers (world SavedData).
        if (activeLootPos != null && playerInventory.player instanceof ServerPlayer spMark) {
            if (activeLootIsCorpse
                    && spMark.level().getBlockEntity(activeLootPos)
                            instanceof TarkovCorpseBlockEntity be) {
                be.markSlotSearched(spMark.getUUID(), lootIdx);
            } else if (!activeLootIsCorpse && spMark.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                com.tarkovinventory.loot.ContainerSearchData.get(sl)
                        .markSearched(activeLootPos, spMark.getUUID(), lootIdx);
            }
        }
    }

    /** Called every server tick while a loot source is open. */
    private void tickLootSearch(ServerPlayer sp) {
        // If we have a current slot, advance it
        if (currentSearchSlot >= 0) {
            // If item was taken mid-search, skip this slot immediately
            if (lootContainer.getItem(currentSearchSlot).isEmpty()) {
                currentSearchSlot = -1;
                slotSearchProgress = 0;
            } else {
                slotSearchProgress++;
                searchData.set(BITMASK_ENTRIES + 1, slotSearchProgress);
                if (slotSearchProgress >= slotSearchRequired) {
                    markSlotSearched(currentSearchSlot);
                    sp.playNotifySound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP,
                            net.minecraft.sounds.SoundSource.PLAYERS, 0.3f, 1.1f + sp.level().getRandom().nextFloat() * 0.3f);
                    currentSearchSlot = -1;
                    slotSearchProgress = 0;
                }
            }
        }

        // Find the next slot if none is active
        if (currentSearchSlot < 0) {
            int next = findNextSearchSlot();
            if (next >= 0) {
                currentSearchSlot = next;
                slotSearchProgress = 0;
                // Random time based on item rarity
                ItemStack item = lootContainer.getItem(next);
                int base = switch (item.getRarity()) {
                    case UNCOMMON -> sp.level().getRandom().nextIntBetweenInclusive(18, 35);
                    case RARE     -> sp.level().getRandom().nextIntBetweenInclusive(25, 50);
                    case EPIC     -> sp.level().getRandom().nextIntBetweenInclusive(35, 65);
                    default       -> sp.level().getRandom().nextIntBetweenInclusive(10, 22);
                };
                slotSearchRequired = base;
                searchData.set(BITMASK_ENTRIES,     currentSearchSlot);
                searchData.set(BITMASK_ENTRIES + 1, 0);
                searchData.set(BITMASK_ENTRIES + 2, slotSearchRequired);
                // Subtle "starting search" rustle sound
                sp.playNotifySound(net.minecraft.sounds.SoundEvents.ITEM_FRAME_ROTATE_ITEM,
                        net.minecraft.sounds.SoundSource.PLAYERS, 0.15f, 0.7f);
            } else {
                // All slots searched
                searchData.set(BITMASK_ENTRIES, -1);
            }
        }
    }

    /** Finds the next loot slot index to search. Respects hovered slot priority. */
    private int findNextSearchSlot() {
        // For containers (non-corpse), only the first activeLootCount slots can hold items.
        int searchLimit = activeLootIsCorpse ? LOOT_SLOTS : Math.min(activeLootCount, LOOT_SLOTS);

        // Prioritise the slot the player is hovering
        if (hoveredLootSlot >= 0 && hoveredLootSlot < searchLimit
                && !isSlotSearchedServer(hoveredLootSlot)
                && lootSlotBase >= 0 && hoveredLootSlot < LOOT_SLOTS) {
            Slot s = slots.get(lootSlotBase + hoveredLootSlot);
            if (s.isActive() && !lootContainer.getItem(hoveredLootSlot).isEmpty())
                return hoveredLootSlot;
        }
        // Otherwise advance left-to-right, top-to-bottom
        for (int i = 0; i < LOOT_SLOTS; i++) {
            if (isSlotSearchedServer(i)) continue;
            if (lootSlotBase < 0) break;
            // Beyond the search limit, or empty/inactive → mark searched and skip instantly
            if (i >= searchLimit) { markSlotSearched(i); continue; }
            Slot s = slots.get(lootSlotBase + i);
            if (!s.isActive() || lootContainer.getItem(i).isEmpty()) {
                markSlotSearched(i);
                continue;
            }
            return i;
        }
        return -1;
    }

    /** Called from the screen when the player hovers over a loot slot. */
    public void setHoveredLootSlot(int lootIdx) { hoveredLootSlot = lootIdx; }

    public int  getSearchCurrentSlot() { return searchData.get(BITMASK_ENTRIES); }
    public int  getSearchProgress()    { return searchData.get(BITMASK_ENTRIES + 1); }
    public int  getSearchRequired()    { return searchData.get(BITMASK_ENTRIES + 2); }

    public int    getLootSlotBase()      { return lootSlotBase; }
    public ItemStack getLootItem(int idx)  { return lootContainer.getItem(idx); }
    /** Returns which loot slots actually contain an item (for renderer colouring). */
    public boolean[] getLootHasItem() {
        boolean[] arr = new boolean[LOOT_COLS * LOOT_ROWS];
        for (int i = 0; i < arr.length && i < LOOT_SLOTS; i++)
            arr[i] = !lootContainer.getItem(i).isEmpty();
        return arr;
    }
    public int    getActiveLootCount()   { return activeLootCount; }
    public String getActiveLootOwner()   { return activeLootOwner; }
    public BlockPos getActiveLootPos()   { return activeLootPos; }

    public int getTotalImageWidth()  { return totalImageWidth; }
    public int getTotalImageHeight() { return totalImageHeight; }
    public int getCenterPanelX()     { return centerPanelX; }
    public int getRightPanelX()      { return rightPanelX; }
    public int getGridBaseY()        { return gridBaseY; }
    public int getRigGridY()         { return rigGridY; }
    public int getPocketY()          { return pocketY; }
    public int getBpPanelY()         { return bpPanelY; }
    public int getRigCols()          { return rigCols; }
    public int getRigRows()          { return rigRows; }
    public int getBpCols()           { return bpCols; }
    public int getBpRows()           { return bpRows; }
    public int getRigStartIndex()    { return RIG_START; }
    public int getRigSlotCount()     { return rigSlotCount; }
    public ItemStack getPocketSlot(int i) {
        if (i < 0 || i >= POCKETS_COUNT) return ItemStack.EMPTY;
        return playerInventory.getItem(i + 2);
    }
}
