package com.tarkovinventory.container;

import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import com.tarkovinventory.capability.IPlayerEquipment;
import com.tarkovinventory.capability.ModCapabilities;
import com.tarkovinventory.client.screen.modules.EquipmentSlotType;
import com.tarkovinventory.client.screen.modules.RightInventoryPanelRenderer;
import com.tarkovinventory.inventory.*;
import com.tarkovinventory.network.S2CEquipmentSyncPacket;
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

    // ── Loot panel ───────────────────────────────────────────────────
    public  static final int LOOT_COLS    = RightInventoryPanelRenderer.COLS;
    public  static final int LOOT_ROWS    = RightInventoryPanelRenderer.LOOT_ROWS;
    public  static final int LOOT_SLOTS   = LOOT_COLS * LOOT_ROWS;
    private static final int LOOT_HEADER_H = RightInventoryPanelRenderer.HEADER_H;

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
            @Nullable BlockPos lootPos, boolean isCorpse, String lootOwner) {}

    // ── Instance fields ──────────────────────────────────────────────
    private final GridInventory      gridInventory;
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
    private int      lootSlotBase      = -1;
    BlockPos         activeLootPos     = null;
    boolean          activeLootIsCorpse = false;
    private String   activeLootOwner   = "";
    private int      activeLootCount   = 0;

    private final int totalImageWidth, totalImageHeight;
    private final int centerPanelX, rightPanelX;
    private final int gridBaseY, rigGridY, pocketY, bpPanelY;
    private final int rigCols, rigRows, bpCols, bpRows;

    private int lastEquipChangeCount = -1;

    // ── THE single private constructor that does all the real work ───

    private TarkovInventoryMenu(int windowId, Inventory playerInv, ConstructorArgs a) {
        super(ModMenuTypes.TARKOV_INVENTORY.get(), windowId);

        this.playerInventory  = playerInv;
        this.cap              = ModCapabilities.get(playerInv.player)
                .orElseThrow(() -> new IllegalStateException("Missing Tarkov capability"));
        this.gridInventory    = cap.getGridInventory();
        this.equipContainer   = new EquipmentContainer(playerInv.player, cap);
        this.rigContainer     = new RigContainer(playerInv.player, RigContainer.Mode.RIG);
        this.backpackContainer = new RigContainer(playerInv.player, RigContainer.Mode.BACKPACK);

        this.rigCols = a.rigCols() > 0 ? a.rigCols() : rigContainer.getCols();
        this.rigRows = a.rigRows() > 0 ? a.rigRows() : rigContainer.getRows();
        this.bpCols  = a.bpCols()  > 0 ? a.bpCols()  : gridInventory.getActiveCols();
        this.bpRows  = a.bpRows()  > 0 ? a.bpRows()  : gridInventory.getActiveRows();

        // Loot state — must be set BEFORE createAllSlots() so slot positions are correct
        this.activeLootCount   = Math.min(a.lootCount(), LOOT_SLOTS);
        this.activeLootPos     = a.lootPos();
        this.activeLootIsCorpse = a.isCorpse();
        this.activeLootOwner   = a.lootOwner();

        // Layout
        int centerW = Math.max(Math.max(bpCols, rigCols), 7) * CELL + 32;
        this.totalImageWidth  = EQUIP_W + PANEL_GAP + centerW + PANEL_GAP + RIGHT_W;
        this.centerPanelX     = EQUIP_W + PANEL_GAP;
        this.rightPanelX      = centerPanelX + centerW + PANEL_GAP;
        this.pocketY          = 0;
        this.rigGridY         = CELL + SECTION_GAP + LABEL_H;
        this.gridBaseY        = rigGridY + rigRows * CELL + SECTION_GAP + LABEL_H;
        this.bpPanelY         = gridBaseY + bpRows * CELL + 30;
        this.totalImageHeight = Math.max(180, bpPanelY + 10) + 20;

        // Slots — loot slot positions are baked from activeLootCount (already set above)
        createAllSlots();

        // Populate loot container
        List<ItemStack> items = a.lootItems();
        for (int i = 0; i < items.size() && i < LOOT_SLOTS; i++) {
            lootContainer.setItem(i, items.get(i));
        }
        lootDirty = false;

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
            // Server-side no-loot: read live container dimensions
            RigContainer rc = new RigContainer(player, RigContainer.Mode.RIG);
            var cap = ModCapabilities.get(player);
            int bc = cap.map(c -> c.getGridInventory().getActiveCols()).orElse(6);
            int br = cap.map(c -> c.getGridInventory().getActiveRows()).orElse(6);
            return new ConstructorArgs(rc.getCols(), rc.getRows(), bc, br, 0, List.of(), null, false, "");
        }
        data.readInt(); // hand (unused)
        int rc = data.readInt(), rr = data.readInt(), bc = data.readInt(), br = data.readInt();
        if (data.readBoolean()) {
            BlockPos pos   = data.readBlockPos();
            boolean corp   = data.readBoolean();
            String owner   = data.readUtf(64);
            int cnt        = data.readVarInt();
            List<ItemStack> items = new ArrayList<>(cnt);
            for (int i = 0; i < cnt; i++) items.add(data.readItem());
            return new ConstructorArgs(rc, rr, bc, br, cnt, items, pos, corp, owner);
        }
        return new ConstructorArgs(rc, rr, bc, br, 0, List.of(), null, false, "");
    }

    private static ConstructorArgs parseFromWorld(Player player, BlockPos lootPos, boolean isCorpse) {
        RigContainer rc  = new RigContainer(player, RigContainer.Mode.RIG);
        var cap          = ModCapabilities.get(player);
        int bpCols       = cap.map(c -> c.getGridInventory().getActiveCols()).orElse(6);
        int bpRows       = cap.map(c -> c.getGridInventory().getActiveRows()).orElse(6);

        Level level  = player.level();
        String owner = "";
        List<ItemStack> items = new ArrayList<>();
        if (isCorpse && level.getBlockEntity(lootPos) instanceof TarkovCorpseBlockEntity be) {
            owner = be.getOwnerName();
            items.addAll(be.getSlottedItems().values());
            items.addAll(be.getInventoryItems());
        } else if (!isCorpse && level.getBlockEntity(lootPos) instanceof Container container) {
            owner = level.getBlockState(lootPos).getBlock().getName().getString();
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) items.add(s.copy());
            }
        }
        return new ConstructorArgs(rc.getCols(), rc.getRows(), bpCols, bpRows,
                items.size(), items, lootPos.immutable(), isCorpse, owner);
    }

    // ── writeDimensions ──────────────────────────────────────────────

    public static void writeDimensions(FriendlyByteBuf buf, Player player) {
        writeDimensions(buf, player, null, false);
    }

    public static void writeDimensions(FriendlyByteBuf buf, Player player,
                                        @Nullable BlockPos lootPos, boolean isCorpse) {
        buf.writeInt(0);
        RigContainer rc = new RigContainer(player, RigContainer.Mode.RIG);
        buf.writeInt(rc.getCols()); buf.writeInt(rc.getRows());
        var capOpt = ModCapabilities.get(player);
        int bpCols = capOpt.map(c -> c.getGridInventory().getActiveCols()).orElse(6);
        int bpRows = capOpt.map(c -> c.getGridInventory().getActiveRows()).orElse(6);
        buf.writeInt(bpCols); buf.writeInt(bpRows);

        if (lootPos != null) {
            buf.writeBoolean(true);
            buf.writeBlockPos(lootPos);
            buf.writeBoolean(isCorpse);
            ConstructorArgs a = parseFromWorld(player, lootPos, isCorpse);
            buf.writeUtf(a.lootOwner(), 64);
            buf.writeVarInt(Math.min(a.lootItems().size(), LOOT_SLOTS));
            for (int i = 0; i < a.lootItems().size() && i < LOOT_SLOTS; i++) {
                buf.writeItem(a.lootItems().get(i));
            }
        } else {
            buf.writeBoolean(false);
        }
    }

    // ── Slot creation ────────────────────────────────────────────────

    private void createAllSlots() {
        // 1. Backpack grid
        for (int i = 0; i < GRID_MAX; i++) {
            final int idx = i;
            int col = i % GridInventory.MAX_COLS, row = i / GridInventory.MAX_COLS;
            boolean on = col < bpCols && row < bpRows;
            addSlot(new Slot(gridInventory, i,
                    on ? centerPanelX + col * CELL : -2000,
                    on ? gridBaseY    + row * CELL : -2000) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return true; }
                @Override public boolean isActive() {
                    int c = idx % GridInventory.MAX_COLS, r = idx / GridInventory.MAX_COLS;
                    return c < bpCols && r < bpRows;
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

        // 6. Rig
        this.rigSlotCount = rigContainer.getContainerSize();
        final boolean rigOK = rigContainer.isItemEquipped();
        for (int i = 0; i < rigSlotCount; i++) {
            int col = i % rigCols, row = i / rigCols;
            addSlot(new TarkovSlot(rigContainer, i,
                    rigOK ? centerPanelX + col * CELL : -2000,
                    rigOK ? rigGridY     + row * CELL : -2000,
                    EquipmentSlotType.UNKNOWN) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return rigOK; }
                @Override public boolean isActive() { return rigOK; }
            });
        }

        // 7. Backpack container
        this.backpackSlotCount = backpackContainer.getContainerSize();
        int bpPC = Math.max(1, backpackContainer.getCols());
        final boolean bpOK = backpackContainer.isItemEquipped();
        for (int i = 0; i < backpackSlotCount; i++) {
            int col = i % bpPC, row = i / bpPC;
            addSlot(new TarkovSlot(backpackContainer, i,
                    bpOK ? centerPanelX + col * CELL : -2000,
                    bpOK ? bpPanelY     + row * CELL : -2000,
                    EquipmentSlotType.UNKNOWN) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return bpOK; }
                @Override public boolean isActive() { return bpOK; }
            });
        }

        // 8. Loot panel — positions baked from activeLootCount (set before this call)
        this.lootSlotBase = slots.size();
        for (int i = 0; i < LOOT_SLOTS; i++) {
            boolean active = i < activeLootCount;
            int sx = active ? rightPanelX + (i % LOOT_COLS) * CELL : -2000;
            int sy = active ? LOOT_HEADER_H + (i / LOOT_COLS) * CELL : -2000;
            addSlot(new Slot(lootContainer, i, sx, sy) {
                @Override public boolean isActive()                      { return this.x != -2000; }
                @Override public boolean mayPlace(@NotNull ItemStack s)  { return false; }
                @Override public boolean mayPickup(@NotNull Player p)    { return this.x != -2000; }
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
            int cc = equipContainer.getChangeCount();
            if (cc != lastEquipChangeCount) {
                lastEquipChangeCount = cc;
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                        new S2CEquipmentSyncPacket(cap.serializeNBT()));
            }
            if (lootDirty && activeLootPos != null) {
                lootDirty = false;
                writeLootBack(sp);
            }
        }
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp && lootDirty && activeLootPos != null) {
            lootDirty = false;
            writeLootBack(sp);
        }
    }

    private void writeLootBack(ServerPlayer sp) {
        Level level = sp.level();
        List<ItemStack> remaining = new ArrayList<>();
        for (int i = 0; i < LOOT_SLOTS; i++) {
            ItemStack s = lootContainer.getItem(i);
            if (!s.isEmpty()) remaining.add(s.copy());
        }
        if (activeLootIsCorpse) {
            if (!(level.getBlockEntity(activeLootPos) instanceof TarkovCorpseBlockEntity be)) return;
            if (remaining.isEmpty()) { level.removeBlock(activeLootPos, false); activeLootPos = null; }
            else be.setInventoryItems(remaining);
        } else {
            if (!(level.getBlockEntity(activeLootPos) instanceof Container container)) return;
            for (int i = 0; i < Math.min(LOOT_SLOTS, container.getContainerSize()); i++)
                container.setItem(i, i < remaining.size() ? remaining.get(i).copy() : ItemStack.EMPTY);
        }
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
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack.copy();

        int rigEnd = RIG_START + rigSlotCount;
        int custEnd = CUSTOM_EQUIP_START + CUSTOM_EQUIP_COUNT;

        if (index < GRID_MAX) {
            if (!moveItemStackTo(stack, VANILLA_ARMOR_START, custEnd, false))
                if (!moveItemStackTo(stack, HOTBAR_START + 2, RIG_START, false))
                    return ItemStack.EMPTY;
        } else if (index >= VANILLA_ARMOR_START && index < VANILLA_ARMOR_START + VANILLA_ARMOR_COUNT) {
            if (!moveItemStackTo(stack, 0, GRID_MAX, false)) return ItemStack.EMPTY;
        } else if (index >= CUSTOM_EQUIP_START && index < custEnd) {
            if (!moveItemStackTo(stack, 0, GRID_MAX, false)) return ItemStack.EMPTY;
        } else if (index >= HOTBAR_START + 2 && index < RIG_START) {
            if (!moveItemStackTo(stack, RIG_START, rigEnd, false))
                if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                    return ItemStack.EMPTY;
        } else if (index >= RIG_START && index < rigEnd) {
            if (!moveItemStackTo(stack, 0, GRID_MAX, false)) return ItemStack.EMPTY;
        } else if (index >= lootSlotBase && index < lootSlotBase + LOOT_SLOTS) {
            // Loot → pockets → rig → backpack grid (never vanilla inventory)
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

    public GridInventory    getGridInventory()    { return gridInventory; }
    public EquipmentContainer getEquipContainer() { return equipContainer; }
    public RigContainer     getRigContainer()     { return rigContainer; }
    public RigContainer     getBackpackContainer() { return backpackContainer; }
    public IPlayerEquipment getCapability()       { return cap; }
    public SimpleContainer  getLootContainer()    { return lootContainer; }

    public int    getLootSlotBase()      { return lootSlotBase; }
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
