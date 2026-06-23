package com.tarkovinventory.container;

import com.tarkovinventory.capability.IPlayerEquipment;
import com.tarkovinventory.capability.ModCapabilities;
import com.tarkovinventory.client.screen.modules.EquipmentSlotType;
import com.tarkovinventory.inventory.*;
import com.tarkovinventory.network.S2CRigSyncPacket;
import com.tarkovinventory.network.ModNetwork;
import com.tarkovinventory.registry.ModMenuTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

/**
 * Menu for the full Tarkov inventory screen.
 *
 * ALL slot positions are calculated here in the constructor because
 * Slot.x and Slot.y are final in Forge 1.20.1.
 *
 * Slot index layout:
 *   [0..143]          grid (12×12 max, active region visible)
 *   [144..147]        vanilla armor (head, chest, legs, feet)
 *   [148..154]        custom equipment (face, ear, rig, pants, knees, armband, backpack)
 *   [155..181]        player main inventory (3×9 = 27)
 *   [182..190]        hotbar (9)
 *   [191..]           rig slots (dynamic) + backpack panel slots (dynamic)
 */
public class TarkovInventoryMenu extends AbstractContainerMenu {

    // ── Slot range constants ────────────────────────────────────────
    public static final int GRID_MAX            = GridInventory.MAX_CELLS;  // 144
    public static final int VANILLA_ARMOR_START = GRID_MAX;                 // 144
    public static final int VANILLA_ARMOR_COUNT = 4;
    public static final int CUSTOM_EQUIP_START  = VANILLA_ARMOR_START + VANILLA_ARMOR_COUNT; // 148
    public static final int CUSTOM_EQUIP_COUNT  = IPlayerEquipment.SLOT_COUNT;                // 7
    public static final int PLAYER_START        = CUSTOM_EQUIP_START + CUSTOM_EQUIP_COUNT;    // 155
    public static final int HOTBAR_START        = PLAYER_START + 27;  // 182
    public static final int RIG_START           = HOTBAR_START + 9;   // 191
    public static final int POCKETS_COUNT       = 7;

    // ── Layout constants (used by screen for background rendering) ──
    public static final int CELL        = 18;
    public static final int PANEL_GAP   = 20;
    public static final int EQUIP_W     = 190;
    public static final int RIGHT_W     = 196;  // 10*18 + 16
    public static final int LABEL_H     = 14;
    public static final int SECTION_GAP = 8;

    // Equipment panel column positions (relative to panel origin)
    private static final int EC1 = 10;   // col1
    private static final int EC2 = 86;   // col2 (center)
    private static final int EC3 = 162;  // col3

    // ── Instance fields ─────────────────────────────────────────────
    private final GridInventory      gridInventory;
    private final EquipmentContainer equipContainer;
    private final IPlayerEquipment   cap;
    private final Inventory          playerInventory;
    private final RigContainer       rigContainer;
    private final RigContainer       backpackContainer;
    private final int                rigSlotCount;
    private final int                backpackSlotCount;

    // Layout info stored for screen rendering
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

    // ── Constructors ────────────────────────────────────────────────

    /** Client-side constructor — reads dimensions from network buffer. */
    public TarkovInventoryMenu(int windowId, Inventory playerInv, FriendlyByteBuf data) {
        super(ModMenuTypes.TARKOV_INVENTORY.get(), windowId);
        this.playerInventory = playerInv;

        this.cap = ModCapabilities.get(playerInv.player)
                .orElseThrow(() -> new IllegalStateException("Player missing Tarkov capability"));
        this.gridInventory = cap.getGridInventory();
        this.equipContainer = new EquipmentContainer(playerInv.player, cap);

        // Read dimensions from buffer (written by server)
        int dataRigCols = 0, dataRigRows = 0, dataBpCols = 0, dataBpRows = 0;
        if (data != null) {
            data.readInt(); // hand (unused, for compat)
            dataRigCols = data.readInt();
            dataRigRows = data.readInt();
            dataBpCols = data.readInt();
            dataBpRows = data.readInt();
        }

        // Determine rig dimensions
        this.rigContainer = new RigContainer(playerInv.player, RigContainer.Mode.RIG);
        if (dataRigCols > 0) {
            this.rigCols = dataRigCols;
            this.rigRows = dataRigRows;
        } else {
            this.rigCols = rigContainer.getCols();
            this.rigRows = rigContainer.getRows();
        }

        // Determine backpack/grid dimensions
        this.backpackContainer = new RigContainer(playerInv.player, RigContainer.Mode.BACKPACK);
        if (dataBpCols > 0) {
            this.bpCols = dataBpCols;
            this.bpRows = dataBpRows;
        } else {
            this.bpCols = gridInventory.getActiveCols();
            this.bpRows = gridInventory.getActiveRows();
        }

        // ── Calculate layout ────────────────────────────────────
        int centerW = Math.max(bpCols, rigCols) * CELL + 32;
        this.totalImageWidth = EQUIP_W + PANEL_GAP + centerW + PANEL_GAP + RIGHT_W;
        this.centerPanelX = EQUIP_W + PANEL_GAP;
        this.rightPanelX = centerPanelX + centerW + PANEL_GAP;

        // Y layout for center panel
        this.pocketY = 0;
        this.rigGridY = CELL + SECTION_GAP + LABEL_H;   // 40
        this.gridBaseY = rigGridY + rigRows * CELL + SECTION_GAP + LABEL_H;
        this.bpPanelY = gridBaseY + bpRows * CELL + 30;

        int equipPanelHeight = 180; // down to SECONDARY slot
        int centerPanelHeight = bpPanelY + 10; // some padding
        this.totalImageHeight = Math.max(equipPanelHeight, centerPanelHeight) + 20;

        // ── Create all slots with final positions ───────────────
        createAllSlots();

        // Send initial rig/backpack sync
        if (playerInv.player instanceof ServerPlayer sp) {
            sendRigSync(sp, 0, rigContainer);
            sendRigSync(sp, 1, backpackContainer);
        }
    }

    /** Fallback constructor for direct creation (e.g., from commands). */
    public TarkovInventoryMenu(int windowId, Inventory playerInv, int ignoredHand) {
        this(windowId, playerInv, null);
    }

    // ── Slot creation ───────────────────────────────────────────────

    private void createAllSlots() {
        // ── 1. Grid slots (144 total, active region visible) ─────
        for (int i = 0; i < GRID_MAX; i++) {
            final int idx = i;
            int col = i % GridInventory.MAX_COLS;
            int row = i / GridInventory.MAX_COLS;
            boolean active = col < bpCols && row < bpRows;
            int sx = active ? centerPanelX + col * CELL : -2000;
            int sy = active ? gridBaseY + row * CELL : -2000;
            addSlot(new Slot(gridInventory, i, sx, sy) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return true; }
                @Override public boolean isActive() {
                    int c = idx % GridInventory.MAX_COLS;
                    int r = idx / GridInventory.MAX_COLS;
                    return c < bpCols && r < bpRows;
                }
            });
        }

        // ── 2. Vanilla armor slots (head, chest, legs, feet) ───
        // playerInv indices: 36=helmet, 37=chest, 38=legs, 39=boots
        int[] armorX = { EC3, EC3, EC3, EC2 };
        int[] armorY = { 0, 20, 40, 80 };
        for (int i = 0; i < 4; i++) {
            addSlot(new TarkovSlot(playerInv, 36 + i, armorX[i], armorY[i],
                    switch (i) {
                        case 0 -> EquipmentSlotType.HEAD;
                        case 1 -> EquipmentSlotType.ARMOR;
                        case 2 -> EquipmentSlotType.PANTS;
                        default -> EquipmentSlotType.BOOTS;
                    }));
        }

        // ── 3. Custom equipment slots (7 from capability) ──────
        int[] custX = { EC1, EC1, EC1, EC3, EC2, -2000, EC1 };
        int[] custY = { 0, 20, 40, 40, 60, -2000, 100 };
        EquipmentSlotType[] custTypes = {
            EquipmentSlotType.FACE,    // 0 = SLOT_FACE
            EquipmentSlotType.EAR,     // 1 = SLOT_EARPIECE
            EquipmentSlotType.RIG,     // 2 = SLOT_RIG
            EquipmentSlotType.PANTS,   // 3 = SLOT_PANTS
            EquipmentSlotType.KNEE,    // 4 = SLOT_KNEES
            EquipmentSlotType.UNKNOWN, // 5 = SLOT_ARMBAND (hidden)
            EquipmentSlotType.BACKPACK // 6 = SLOT_ON_BACK
        };
        for (int i = 0; i < CUSTOM_EQUIP_COUNT; i++) {
            addSlot(new TarkovSlot(equipContainer, 4 + i, custX[i], custY[i], custTypes[i]));
        }

        // ── 4. Player main inventory (3×9 = 27) ────────────────
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                        rightPanelX + col * CELL, 20 + row * CELL));

        // ── 5. Hotbar (9 slots) ─────────────────────────────────
        // Hotbar 0 → PRIMARY, Hotbar 1 → SECONDARY (in equipment panel)
        // Hotbar 2-8 → pockets (in center panel top row)
        addSlot(new Slot(playerInv, 0, EC1, 140));   // PRIMARY
        addSlot(new Slot(playerInv, 1, EC1, 160));   // SECONDARY
        for (int i = 0; i < POCKETS_COUNT; i++) {
            addSlot(new Slot(playerInv, i + 2,
                    centerPanelX + i * CELL, pocketY));
        }

        // ── 6. Rig panel slots (dynamic count) ─────────────────
        this.rigSlotCount = rigContainer.getContainerSize();
        for (int i = 0; i < rigSlotCount; i++) {
            int col = i % rigCols;
            int row = i / rigCols;
            addSlot(new TarkovSlot(rigContainer, i,
                    centerPanelX + col * CELL, rigGridY + row * CELL,
                    EquipmentSlotType.UNKNOWN) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return true; }
            });
        }

        // ── 7. Backpack panel slots (dynamic count) ────────────
        this.backpackSlotCount = backpackContainer.getContainerSize();
        int bpPanelCols = Math.max(1, backpackContainer.getCols());
        for (int i = 0; i < backpackSlotCount; i++) {
            int col = i % bpPanelCols;
            int row = i / bpPanelCols;
            addSlot(new TarkovSlot(backpackContainer, i,
                    centerPanelX + col * CELL, bpPanelY + row * CELL,
                    EquipmentSlotType.UNKNOWN) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return true; }
            });
        }
    }

    // ── Sync helper ─────────────────────────────────────────────────

    private void sendRigSync(ServerPlayer player, int mode, RigContainer container) {
        CompoundTag data = container.getRigInventory() != null
                ? container.getRigInventory().serializeNBT()
                : new CompoundTag();
        ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CRigSyncPacket(mode, data, container.getCols(), container.getRows()));
    }

    public void syncRigToClient(int mode) {
        if (playerInventory.player instanceof ServerPlayer sp) {
            RigContainer c = mode == 0 ? rigContainer : backpackContainer;
            c.reloadFromItem();
            sendRigSync(sp, mode, c);
        }
    }

    /** Write rig/backpack dimensions to a network buffer. */
    public static void writeDimensions(FriendlyByteBuf buf, Player player) {
        buf.writeInt(0); // hand (unused)
        // Read rig dims from player's equipped item
        RigContainer rc = new RigContainer(player, RigContainer.Mode.RIG);
        buf.writeInt(rc.getCols());
        buf.writeInt(rc.getRows());
        // Read backpack dims from capability (fall back to default)
        int bpCols = 6;
        int bpRows = 6;
        var capOpt = ModCapabilities.get(player);
        if (capOpt.isPresent()) {
            bpCols = capOpt.resolve().map(c -> c.getGridInventory().getActiveCols()).orElse(6);
            bpRows = capOpt.resolve().map(c -> c.getGridInventory().getActiveRows()).orElse(6);
        }
        buf.writeInt(bpCols);
        buf.writeInt(bpRows);
    }

    // ── Accessors ───────────────────────────────────────────────────

    public GridInventory getGridInventory() { return gridInventory; }
    public EquipmentContainer getEquipContainer() { return equipContainer; }
    public RigContainer getRigContainer() { return rigContainer; }
    public RigContainer getBackpackContainer() { return backpackContainer; }
    public IPlayerEquipment getCapability() { return cap; }

    public int getRigStartIndex() { return RIG_START; }
    public int getBackpackPanelStartIndex() { return RIG_START + rigSlotCount; }
    public int getRigSlotCount() { return rigSlotCount; }
    public int getBackpackPanelSlotCount() { return backpackSlotCount; }
    public int getTotalSlots() { return slots.size(); }

    public int getTotalImageWidth() { return totalImageWidth; }
    public int getTotalImageHeight() { return totalImageHeight; }
    public int getCenterPanelX() { return centerPanelX; }
    public int getRightPanelX() { return rightPanelX; }
    public int getGridBaseY() { return gridBaseY; }
    public int getRigGridY() { return rigGridY; }
    public int getPocketY() { return pocketY; }
    public int getBpPanelY() { return bpPanelY; }
    public int getRigCols() { return rigCols; }
    public int getRigRows() { return rigRows; }
    public int getBpCols() { return bpCols; }
    public int getBpRows() { return bpRows; }

    public ItemStack getPocketSlot(int i) {
        if (i < 0 || i >= POCKETS_COUNT) return ItemStack.EMPTY;
        return playerInventory.getItem(i + 2);
    }

    // ── Shift-click logic ───────────────────────────────────────────

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return result;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int rigEnd   = RIG_START + rigSlotCount;
        int bpEnd    = rigEnd + backpackSlotCount;
        int customEquipEnd = CUSTOM_EQUIP_START + CUSTOM_EQUIP_COUNT;

        if (index < GRID_MAX) {
            if (!moveItemStackTo(stack, VANILLA_ARMOR_START, customEquipEnd, false))
                if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false))
                    return ItemStack.EMPTY;
        } else if (index >= VANILLA_ARMOR_START && index < VANILLA_ARMOR_START + VANILLA_ARMOR_COUNT) {
            if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false))
                if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                    return ItemStack.EMPTY;
        } else if (index >= CUSTOM_EQUIP_START && index < customEquipEnd) {
            if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false))
                if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                    return ItemStack.EMPTY;
        } else if (index >= PLAYER_START && index < HOTBAR_START) {
            if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                if (!moveItemStackTo(stack, HOTBAR_START, RIG_START, false))
                    return ItemStack.EMPTY;
        } else if (index >= HOTBAR_START && index < RIG_START) {
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_START + 27, false))
                if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                    return ItemStack.EMPTY;
        } else if (index >= RIG_START && index < rigEnd) {
            if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false))
                    return ItemStack.EMPTY;
        } else if (index >= rigEnd && index < bpEnd) {
            if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false))
                    return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(@NotNull Player player) { return true; }
}
