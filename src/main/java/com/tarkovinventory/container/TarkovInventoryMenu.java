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
 * Slot layout:
 *   [0..143]         backpack grid (12×12 max)
 *   [144..147]       vanilla armor (head, chest, legs, feet)
 *   [148..154]       custom equipment (face, ear, rig, pants, knees, armband, backpack)
 *   [155..181]       player main inventory — off-screen, never shown
 *   [182..190]       hotbar: [0]=PRIMARY, [1]=SECONDARY, [2..8]=pockets
 *   [191..190+rig]   rig slots (dynamic)
 *   [...+bp]         backpack container slots (dynamic)
 *   [...+80]         loot panel slots (80 pre-allocated, hidden when no loot source)
 */
public class TarkovInventoryMenu extends AbstractContainerMenu {

    // ── Slot range constants ────────────────────────────────────────
    public static final int GRID_MAX            = GridInventory.MAX_CELLS;  // 144
    public static final int VANILLA_ARMOR_START = GRID_MAX;
    public static final int VANILLA_ARMOR_COUNT = 4;
    public static final int CUSTOM_EQUIP_START  = VANILLA_ARMOR_START + VANILLA_ARMOR_COUNT; // 148
    public static final int CUSTOM_EQUIP_COUNT  = IPlayerEquipment.SLOT_COUNT;
    public static final int PLAYER_START        = CUSTOM_EQUIP_START + CUSTOM_EQUIP_COUNT;   // 155
    public static final int HOTBAR_START        = PLAYER_START + 27;  // 182
    public static final int RIG_START           = HOTBAR_START + 9;   // 191
    public static final int POCKETS_COUNT       = 7;

    // ── Loot panel (real Forge slots, pre-allocated) ───────────────
    public  static final int LOOT_COLS    = RightInventoryPanelRenderer.COLS;       // 10
    public  static final int LOOT_ROWS    = RightInventoryPanelRenderer.LOOT_ROWS;  // 8
    public  static final int LOOT_SLOTS   = LOOT_COLS * LOOT_ROWS;                  // 80
    private static final int LOOT_HEADER_H = RightInventoryPanelRenderer.HEADER_H; // 14

    // ── Layout constants ────────────────────────────────────────────
    public static final int CELL        = 18;
    public static final int PANEL_GAP   = 20;
    public static final int EQUIP_W     = 190;
    public static final int RIGHT_W     = RightInventoryPanelRenderer.panelWidth() + 16; // 196
    public static final int LABEL_H     = 14;
    public static final int SECTION_GAP = 8;

    private static final int EC1 = 10;
    private static final int EC2 = 86;
    private static final int EC3 = 162;

    // ── Instance fields ─────────────────────────────────────────────
    private final GridInventory      gridInventory;
    private final EquipmentContainer equipContainer;
    private final IPlayerEquipment   cap;
    private final Inventory          playerInventory;
    private final RigContainer       rigContainer;
    private final RigContainer       backpackContainer;
    private int                      rigSlotCount;
    private int                      backpackSlotCount;

    // ── Loot panel ──────────────────────────────────────────────────
    private final SimpleContainer lootContainer = new SimpleContainer(LOOT_SLOTS) {
        @Override public void setChanged() { super.setChanged(); lootDirty = true; }
    };
    private boolean  lootDirty        = false;
    private int      lootSlotBase     = -1;   // set after createAllSlots()
    BlockPos         activeLootPos     = null; // package-visible for screen
    boolean          activeLootIsCorpse = false;
    private String   activeLootOwner  = "";
    private int      activeLootCount  = 0;

    // ── Layout stored for screen rendering ─────────────────────────
    private final int totalImageWidth;
    private final int totalImageHeight;
    private final int centerPanelX;
    private final int rightPanelX;
    private final int gridBaseY;
    private final int rigGridY;
    private final int pocketY;
    private final int bpPanelY;
    private final int rigCols;
    private final int rigRows;
    private final int bpCols;
    private final int bpRows;

    private int lastEquipChangeCount = -1;

    // ── Constructors ────────────────────────────────────────────────

    /** Client-side constructor (reads dims + loot from network buffer). */
    public TarkovInventoryMenu(int windowId, Inventory playerInv, FriendlyByteBuf data) {
        super(ModMenuTypes.TARKOV_INVENTORY.get(), windowId);
        this.playerInventory = playerInv;
        this.cap = ModCapabilities.get(playerInv.player)
                .orElseThrow(() -> new IllegalStateException("Player missing Tarkov capability"));
        this.gridInventory    = cap.getGridInventory();
        this.equipContainer   = new EquipmentContainer(playerInv.player, cap);

        int dataRigCols = 0, dataRigRows = 0, dataBpCols = 0, dataBpRows = 0;
        List<ItemStack> dataLootItems = List.of();
        boolean dataHasLoot = false;

        if (data != null) {
            data.readInt(); // hand (unused)
            dataRigCols = data.readInt();
            dataRigRows = data.readInt();
            dataBpCols  = data.readInt();
            dataBpRows  = data.readInt();

            dataHasLoot = data.readBoolean();
            if (dataHasLoot) {
                this.activeLootPos      = data.readBlockPos();
                this.activeLootIsCorpse = data.readBoolean();
                this.activeLootOwner    = data.readUtf(64);
                int cnt = data.readVarInt();
                List<ItemStack> tmp = new ArrayList<>(cnt);
                for (int i = 0; i < cnt; i++) tmp.add(data.readItem());
                dataLootItems = tmp;
            }
        }

        this.rigContainer = new RigContainer(playerInv.player, RigContainer.Mode.RIG);
        this.rigCols      = dataRigCols > 0 ? dataRigCols : rigContainer.getCols();
        this.rigRows      = dataRigRows > 0 ? dataRigRows : rigContainer.getRows();

        this.backpackContainer = new RigContainer(playerInv.player, RigContainer.Mode.BACKPACK);
        this.bpCols = dataBpCols > 0 ? dataBpCols : gridInventory.getActiveCols();
        this.bpRows = dataBpRows > 0 ? dataBpRows : gridInventory.getActiveRows();

        this.activeLootCount = Math.min(dataLootItems.size(), LOOT_SLOTS);

        int centerW = Math.max(Math.max(bpCols, rigCols), 7) * CELL + 32;
        this.totalImageWidth  = EQUIP_W + PANEL_GAP + centerW + PANEL_GAP + RIGHT_W;
        this.centerPanelX     = EQUIP_W + PANEL_GAP;
        this.rightPanelX      = centerPanelX + centerW + PANEL_GAP;
        this.pocketY          = 0;
        this.rigGridY         = CELL + SECTION_GAP + LABEL_H;
        this.gridBaseY        = rigGridY + rigRows * CELL + SECTION_GAP + LABEL_H;
        this.bpPanelY         = gridBaseY + bpRows * CELL + 30;
        int equipPanelHeight  = 180;
        int centerPanelHeight = bpPanelY + 10;
        this.totalImageHeight = Math.max(equipPanelHeight, centerPanelHeight) + 20;

        createAllSlots();

        // Populate loot container from buf data (client) or block entity (server)
        for (int i = 0; i < dataLootItems.size() && i < LOOT_SLOTS; i++) {
            lootContainer.setItem(i, dataLootItems.get(i));
        }
        positionLootSlots(activeLootCount);

        if (playerInv.player instanceof ServerPlayer sp) {
            sendRigSync(sp, 0, rigContainer);
            sendRigSync(sp, 1, backpackContainer);
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp),
                    new S2CEquipmentSyncPacket(cap.serializeNBT()));
        }
    }

    /** Server-side constructor — no loot source. */
    public TarkovInventoryMenu(int windowId, Inventory playerInv, int ignored) {
        this(windowId, playerInv, (FriendlyByteBuf) null);
    }

    /** Server-side constructor — with a loot source (corpse or container). */
    public TarkovInventoryMenu(int windowId, Inventory playerInv,
                                BlockPos lootPos, boolean isCorpse) {
        this(windowId, playerInv, (FriendlyByteBuf) null);
        this.activeLootPos      = lootPos;
        this.activeLootIsCorpse = isCorpse;
        populateLootServerSide(playerInv.player, lootPos, isCorpse);
    }

    // ── Loot helpers ────────────────────────────────────────────────

    private void populateLootServerSide(Player player, BlockPos pos, boolean isCorpse) {
        Level level = player.level();
        List<ItemStack> items = new ArrayList<>();

        if (isCorpse && level.getBlockEntity(pos) instanceof TarkovCorpseBlockEntity be) {
            activeLootOwner = be.getOwnerName();
            items.addAll(be.getSlottedItems().values());
            items.addAll(be.getInventoryItems());
        } else if (!isCorpse && level.getBlockEntity(pos) instanceof Container container) {
            activeLootOwner = level.getBlockState(pos).getBlock().getName().getString();
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack s = container.getItem(i);
                if (!s.isEmpty()) items.add(s.copy());
            }
        }

        int count = Math.min(items.size(), LOOT_SLOTS);
        for (int i = 0; i < count; i++) lootContainer.setItem(i, items.get(i));
        activeLootCount = count;
        positionLootSlots(count);
        lootDirty = false; // fresh from source, no writeback needed yet
    }

    /** Reposition the 80 pre-allocated loot slots. Slots 0..count-1 go to the panel; rest are hidden. */
    private void positionLootSlots(int count) {
        if (lootSlotBase < 0) return;
        for (int i = 0; i < LOOT_SLOTS; i++) {
            Slot s = slots.get(lootSlotBase + i);
            if (i < count) {
                s.x = rightPanelX + (i % LOOT_COLS) * CELL;
                s.y = LOOT_HEADER_H + (i / LOOT_COLS) * CELL;
            } else {
                s.x = -2000;
                s.y = -2000;
            }
        }
    }

    private void writeLootBack(ServerPlayer sp) {
        if (activeLootPos == null) return;
        Level level = sp.level();

        List<ItemStack> remaining = new ArrayList<>();
        for (int i = 0; i < LOOT_SLOTS; i++) {
            ItemStack s = lootContainer.getItem(i);
            if (!s.isEmpty()) remaining.add(s.copy());
        }

        if (activeLootIsCorpse) {
            if (!(level.getBlockEntity(activeLootPos) instanceof TarkovCorpseBlockEntity be)) return;
            if (remaining.isEmpty()) {
                level.removeBlock(activeLootPos, false);
                activeLootPos = null;
            } else {
                be.setInventoryItems(remaining);
            }
        } else {
            if (!(level.getBlockEntity(activeLootPos) instanceof Container container)) return;
            for (int i = 0; i < Math.min(LOOT_SLOTS, container.getContainerSize()); i++) {
                container.setItem(i, i < remaining.size() ? remaining.get(i).copy() : ItemStack.EMPTY);
            }
        }
    }

    // ── Slot creation ───────────────────────────────────────────────

    private void createAllSlots() {
        // 1. Backpack grid
        for (int i = 0; i < GRID_MAX; i++) {
            final int idx = i;
            int col = i % GridInventory.MAX_COLS;
            int row = i / GridInventory.MAX_COLS;
            boolean active = col < bpCols && row < bpRows;
            int sx = active ? centerPanelX + col * CELL : -2000;
            int sy = active ? gridBaseY + row * CELL     : -2000;
            addSlot(new Slot(gridInventory, i, sx, sy) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return true; }
                @Override public boolean isActive() {
                    int c = idx % GridInventory.MAX_COLS, r = idx / GridInventory.MAX_COLS;
                    return c < bpCols && r < bpRows;
                }
            });
        }

        // 2. Vanilla armor (HEAD=39, CHEST=38, LEGS=37, FEET=36)
        int[] armorIdx   = { 39, 38, 37, 36 };
        int[] armorX     = { EC3, EC3, EC3, EC2 };
        int[] armorY     = { 0, 20, 40, 80 };
        EquipmentSlotType[] armorTypes = {
                EquipmentSlotType.HEAD, EquipmentSlotType.ARMOR,
                EquipmentSlotType.PANTS, EquipmentSlotType.BOOTS };
        for (int i = 0; i < 4; i++)
            addSlot(new TarkovSlot(playerInventory, armorIdx[i], armorX[i], armorY[i], armorTypes[i]));

        // 3. Custom equipment capability (FACE/EAR/RIG hidden PANTS/KNEES/ARMBAND/BACKPACK)
        int[] custX = { EC1, EC1, EC1, -2000, EC2, -2000, EC1 };
        int[] custY = { 0, 20, 40, -2000, 60, -2000, 100 };
        EquipmentSlotType[] custTypes = {
                EquipmentSlotType.FACE, EquipmentSlotType.EAR, EquipmentSlotType.RIG,
                EquipmentSlotType.PANTS, EquipmentSlotType.KNEE,
                EquipmentSlotType.UNKNOWN, EquipmentSlotType.BACKPACK };
        for (int i = 0; i < CUSTOM_EQUIP_COUNT; i++)
            addSlot(new TarkovSlot(equipContainer, 4 + i, custX[i], custY[i], custTypes[i]));

        // 4. Player main inventory — off-screen (never shown, needed for Forge tracking)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInventory, col + row * 9 + 9, -2000, -2000) {
                    @Override public boolean isActive() { return false; }
                });

        // 5. Hotbar: PRIMARY, SECONDARY, then pockets 0-6
        addSlot(new Slot(playerInventory, 0, EC1, 140));
        addSlot(new Slot(playerInventory, 1, EC1, 160));
        for (int i = 0; i < POCKETS_COUNT; i++)
            addSlot(new Slot(playerInventory, i + 2, centerPanelX + i * CELL, pocketY));

        // 6. Rig slots
        this.rigSlotCount = rigContainer.getContainerSize();
        final boolean rigEquipped = rigContainer.isItemEquipped();
        for (int i = 0; i < rigSlotCount; i++) {
            int col = i % rigCols, row = i / rigCols;
            int sx = rigEquipped ? centerPanelX + col * CELL : -2000;
            int sy = rigEquipped ? rigGridY + row * CELL      : -2000;
            addSlot(new TarkovSlot(rigContainer, i, sx, sy, EquipmentSlotType.UNKNOWN) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return rigEquipped; }
                @Override public boolean isActive() { return rigEquipped; }
            });
        }

        // 7. Backpack container slots
        this.backpackSlotCount = backpackContainer.getContainerSize();
        int bpPanelCols = Math.max(1, backpackContainer.getCols());
        final boolean bpEquipped = backpackContainer.isItemEquipped();
        for (int i = 0; i < backpackSlotCount; i++) {
            int col = i % bpPanelCols, row = i / bpPanelCols;
            int sx = bpEquipped ? centerPanelX + col * CELL : -2000;
            int sy = bpEquipped ? bpPanelY + row * CELL      : -2000;
            addSlot(new TarkovSlot(backpackContainer, i, sx, sy, EquipmentSlotType.UNKNOWN) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return bpEquipped; }
                @Override public boolean isActive() { return bpEquipped; }
            });
        }

        // 8. Loot panel — 80 pre-allocated Forge slots, hidden until a source is opened
        this.lootSlotBase = slots.size();
        for (int i = 0; i < LOOT_SLOTS; i++) {
            addSlot(new Slot(lootContainer, i, -2000, -2000) {
                @Override public boolean isActive()                           { return this.x != -2000; }
                @Override public boolean mayPlace(@NotNull ItemStack s)       { return false; }
                @Override public boolean mayPickup(@NotNull Player player)    { return this.x != -2000; }
            });
        }
    }

    // ── Sync helpers ────────────────────────────────────────────────

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

    // ── writeDimensions ─────────────────────────────────────────────

    public static void writeDimensions(FriendlyByteBuf buf, Player player) {
        writeDimensions(buf, player, null, false);
    }

    public static void writeDimensions(FriendlyByteBuf buf, Player player,
                                        @Nullable BlockPos lootPos, boolean isCorpse) {
        buf.writeInt(0);
        RigContainer rc = new RigContainer(player, RigContainer.Mode.RIG);
        buf.writeInt(rc.getCols()); buf.writeInt(rc.getRows());
        int bpCols = 6, bpRows = 6;
        var capOpt = ModCapabilities.get(player);
        if (capOpt.isPresent()) {
            bpCols = capOpt.resolve().map(c -> c.getGridInventory().getActiveCols()).orElse(6);
            bpRows = capOpt.resolve().map(c -> c.getGridInventory().getActiveRows()).orElse(6);
        }
        buf.writeInt(bpCols); buf.writeInt(bpRows);

        if (lootPos != null) {
            buf.writeBoolean(true);
            buf.writeBlockPos(lootPos);
            buf.writeBoolean(isCorpse);
            // Write owner + items for client
            Level level = player.level();
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
            buf.writeUtf(owner, 64);
            int count = Math.min(items.size(), LOOT_SLOTS);
            buf.writeVarInt(count);
            for (int i = 0; i < count; i++) buf.writeItem(items.get(i));
        } else {
            buf.writeBoolean(false);
        }
    }

    // ── LootMenuProvider ────────────────────────────────────────────

    /**
     * MenuProvider that opens the Tarkov inventory pre-loaded with a loot source.
     * Use for both corpse blocks and generic containers.
     */
    public static final class LootMenuProvider implements MenuProvider {
        private final BlockPos pos;
        private final boolean  isCorpse;

        public LootMenuProvider(BlockPos pos, boolean isCorpse) {
            this.pos      = pos;
            this.isCorpse = isCorpse;
        }

        @Override public @NotNull Component getDisplayName() {
            return Component.literal("Tarkov Inventory");
        }
        @Override public @NotNull AbstractContainerMenu createMenu(
                int id, @NotNull Inventory inv, @NotNull Player player) {
            return new TarkovInventoryMenu(id, inv, pos, isCorpse);
        }
    }

    // ── Shift-click ─────────────────────────────────────────────────

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int rigEnd  = RIG_START + rigSlotCount;
        int bpEnd   = rigEnd + backpackSlotCount;
        int custEnd = CUSTOM_EQUIP_START + CUSTOM_EQUIP_COUNT;

        if (index < GRID_MAX) {
            // Backpack grid → armor/equip → pockets
            if (!moveItemStackTo(stack, VANILLA_ARMOR_START, custEnd, false))
                if (!moveItemStackTo(stack, HOTBAR_START + 2, RIG_START, false))
                    return ItemStack.EMPTY;
        } else if (index >= VANILLA_ARMOR_START && index < VANILLA_ARMOR_START + VANILLA_ARMOR_COUNT) {
            if (!moveItemStackTo(stack, 0, GRID_MAX, false)) return ItemStack.EMPTY;
        } else if (index >= CUSTOM_EQUIP_START && index < custEnd) {
            if (!moveItemStackTo(stack, 0, GRID_MAX, false)) return ItemStack.EMPTY;
        } else if (index >= HOTBAR_START + 2 && index < RIG_START) {
            // Pockets → rig → grid
            if (!moveItemStackTo(stack, RIG_START, rigEnd, false))
                if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                    return ItemStack.EMPTY;
        } else if (index >= RIG_START && index < rigEnd) {
            if (!moveItemStackTo(stack, 0, GRID_MAX, false)) return ItemStack.EMPTY;
        } else if (index >= rigEnd && index < bpEnd) {
            if (!moveItemStackTo(stack, 0, GRID_MAX, false)) return ItemStack.EMPTY;
        } else if (index >= lootSlotBase && index < lootSlotBase + LOOT_SLOTS) {
            // Loot → pockets → rig → backpack grid (NEVER vanilla inventory)
            if (!moveItemStackTo(stack, HOTBAR_START + 2, RIG_START, false))
                if (!moveItemStackTo(stack, RIG_START, rigEnd, false))
                    if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                        return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return result;
    }

    @Override public boolean stillValid(@NotNull Player player) { return true; }

    // ── Accessors ───────────────────────────────────────────────────

    public GridInventory getGridInventory()         { return gridInventory; }
    public EquipmentContainer getEquipContainer()   { return equipContainer; }
    public RigContainer getRigContainer()           { return rigContainer; }
    public RigContainer getBackpackContainer()       { return backpackContainer; }
    public IPlayerEquipment getCapability()         { return cap; }
    public SimpleContainer getLootContainer()       { return lootContainer; }

    public int getLootSlotBase()     { return lootSlotBase; }
    public int getActiveLootCount()  { return activeLootCount; }
    public String getActiveLootOwner() { return activeLootOwner; }
    public BlockPos getActiveLootPos() { return activeLootPos; }

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
