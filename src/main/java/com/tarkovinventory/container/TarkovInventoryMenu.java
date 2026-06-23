package com.tarkovinventory.container;

import com.tarkovinventory.capability.IPlayerEquipment;
import com.tarkovinventory.capability.ModCapabilities;
import com.tarkovinventory.client.screen.modules.EquipmentSlotType;
import com.tarkovinventory.inventory.*;
import com.tarkovinventory.network.S2CRigSyncPacket;
import com.tarkovinventory.network.ModNetwork;
import com.tarkovinventory.registry.ModMenuTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Menu for the full Tarkov inventory screen.
 *
 * Slot layout (all indices are into menu.slots):
 *   [0  .. 143]                backpack grid (GridInventory, 12×12 max)
 *   [144 .. 146]               equipment (earpiece, armband, on-back)
 *   [147 .. 173]               player main inventory (3×9 = 27)
 *   [174 .. 182]               hotbar (9)
 *   [183 .. 183+rigCount-1]    rig panel slots (dynamic)
 *   [183+rigCount ..]          backpack panel slots (dynamic, separate from grid)
 *
 * Pocket slots 0-6 map to hotbar indices 2-8.
 * Primary weapon = hotbar 0, Secondary = hotbar 1.
 */
public class TarkovInventoryMenu extends AbstractContainerMenu {

    // ── Slot range constants ────────────────────────────────────────
    public static final int GRID_MAX      = GridInventory.MAX_CELLS;  // 144
    public static final int GRID_SLOTS    = GridInventory.MAX_CELLS; // 144 (all reachable)
    public static final int EQUIP_START   = GRID_MAX;   // 144
    public static final int EQUIP_SLOTS   = IPlayerEquipment.SLOT_COUNT; // 3
    public static final int PLAYER_START  = EQUIP_START + EQUIP_SLOTS; // 147
    public static final int HOTBAR_START  = PLAYER_START + 27;           // 174
    public static final int RIG_START     = HOTBAR_START + 9;            // 183
    public static final int POCKETS_COUNT = 7;

    // ── Instance fields ─────────────────────────────────────────────
    private final GridInventory      gridInventory;
    private final EquipmentContainer equipContainer;
    private final IPlayerEquipment   cap;
    private final Inventory          playerInventory;
    private RigContainer             rigContainer;
    private RigContainer             backpackContainer;
    private int                      rigSlotCount;
    private int                      backpackSlotCount;

    public TarkovInventoryMenu(int windowId, Inventory playerInv, int ignoredHand) {
        super(ModMenuTypes.TARKOV_INVENTORY.get(), windowId);
        this.playerInventory = playerInv;

        this.cap = ModCapabilities.get(playerInv.player)
                .orElseThrow(() -> new IllegalStateException("Player missing Tarkov capability"));
        this.gridInventory = cap.getGridInventory();
        this.equipContainer = new EquipmentContainer(cap);

        // ── 1. Grid slots (backpack grid — 144 total, active subset positioned on screen) ──
        for (int i = 0; i < GRID_MAX; i++) {
            final int idx = i;
            addSlot(new Slot(gridInventory, i, -1000, -1000) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return true; }
                @Override public boolean isActive() {
                    int col = idx % GridInventory.MAX_COLS;
                    int row = idx / GridInventory.MAX_COLS;
                    return col < gridInventory.getActiveCols() && row < gridInventory.getActiveRows();
                }
            });
        }

        // ── 2. Equipment slots (earpiece, armband, on-back) ─────────
        addSlot(new TarkovSlot(equipContainer, 0, -1000, -1000, EquipmentSlotType.EAR));
        addSlot(new TarkovSlot(equipContainer, 1, -1000, -1000, EquipmentSlotType.UNKNOWN));
        addSlot(new TarkovSlot(equipContainer, 2, -1000, -1000, EquipmentSlotType.BACKPACK));

        // ── 3. Player main inventory (3×9 = 27) ────────────────────
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                addSlot(new Slot(playerInv, col + row * 9 + 9, -1000, -1000));

        // ── 4. Hotbar (9 slots) ─────────────────────────────────────
        for (int col = 0; col < 9; col++)
            addSlot(new Slot(playerInv, col, -1000, -1000));

        // ── 5. Rig slots (dynamic count from equipped rig item) ─────
        this.rigContainer = new RigContainer(playerInv.player, RigContainer.Mode.RIG);
        this.rigSlotCount = rigContainer.getContainerSize();
        for (int i = 0; i < rigSlotCount; i++) {
            addSlot(new TarkovSlot(rigContainer, i, -1000, -1000, EquipmentSlotType.UNKNOWN) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return true; }
            });
        }

        // ── 6. Backpack panel slots (dynamic — separate from grid) ──
        this.backpackContainer = new RigContainer(playerInv.player, RigContainer.Mode.BACKPACK);
        this.backpackSlotCount = backpackContainer.getContainerSize();
        for (int i = 0; i < backpackSlotCount; i++) {
            addSlot(new TarkovSlot(backpackContainer, i, -1000, -1000, EquipmentSlotType.UNKNOWN) {
                @Override public boolean mayPlace(@NotNull ItemStack s) { return true; }
            });
        }

        // Send initial rig/backpack contents to client
        if (playerInv.player instanceof ServerPlayer sp) {
            sendRigSync(sp, 0, rigContainer);
            sendRigSync(sp, 1, backpackContainer);
        }
    }

    // ── Sync helper ─────────────────────────────────────────────────

    private void sendRigSync(ServerPlayer player, int mode, RigContainer container) {
        CompoundTag data = container.getRigInventory() != null
                ? container.getRigInventory().serializeNBT()
                : new CompoundTag();
        ModNetwork.CHANNEL.sendTo(player,
                new S2CRigSyncPacket(mode, data, container.getCols(), container.getRows()));
    }

    /** Call from screen when rig/backpack contents need re-sync. */
    public void syncRigToClient(int mode) {
        if (playerInventory.player instanceof ServerPlayer sp) {
            RigContainer c = mode == 0 ? rigContainer : backpackContainer;
            c.reloadFromItem();
            sendRigSync(sp, mode, c);
        }
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

    /** Pocket slot i → hotbar index i+2 */
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

        if (index < GRID_MAX) {
            // Grid → player inventory → hotbar
            if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false))
                return ItemStack.EMPTY;
        } else if (index >= EQUIP_START && index < PLAYER_START) {
            // Equipment → grid → player inventory
            if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false))
                    return ItemStack.EMPTY;
        } else if (index >= PLAYER_START && index < HOTBAR_START) {
            // Player inventory → grid → hotbar
            if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                if (!moveItemStackTo(stack, HOTBAR_START, RIG_START, false))
                    return ItemStack.EMPTY;
        } else if (index >= HOTBAR_START && index < RIG_START) {
            // Hotbar → player inventory → grid
            if (!moveItemStackTo(stack, PLAYER_START, PLAYER_START + 27, false))
                if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                    return ItemStack.EMPTY;
        } else if (index >= RIG_START && index < rigEnd) {
            // Rig → grid → player inventory
            if (!moveItemStackTo(stack, 0, GRID_MAX, false))
                if (!moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false))
                    return ItemStack.EMPTY;
        } else if (index >= rigEnd && index < bpEnd) {
            // Backpack panel → grid → player inventory
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
