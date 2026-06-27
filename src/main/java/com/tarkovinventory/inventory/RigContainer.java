package com.tarkovinventory.inventory;

import com.tarkovinventory.capability.IPlayerEquipment;
import com.tarkovinventory.compat.CuriosCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Container backed by a rig or backpack item's NBT.
 *
 * Loads the RigInventory from the equipped item's NBT tag on construction
 * and writes it back on every setItem / removeItem. This lets Forge's
 * standard Slot objects interact with NBT-stored inventories and sync
 * automatically via broadcastChanges().
 *
 * Mode:
 *   RIG      → reads Curios "body" slot, falls back to EquipmentSlot.CHEST
 *   BACKPACK → reads capability SLOT_ON_BACK (or Curios "back")
 */
public class RigContainer implements Container {

    public enum Mode { RIG, BACKPACK }

    private final Player player;
    private final Mode mode;
    private RigInventory rigInventory;
    private int changeCount;

    public RigContainer(Player player, Mode mode) {
        this.player = player;
        this.mode = mode;
        reloadFromItem();
    }

    // ── Load / save from the equipped item ──────────────────────────

    public void reloadFromItem() {
        ItemStack equipped = getEquippedItem();
        if (equipped.isEmpty()) {
            int cols = mode == Mode.RIG ? RigSizes.DEFAULT_COLS : BackpackSizes.DEFAULT_COLS;
            int rows = mode == Mode.RIG ? RigSizes.DEFAULT_ROWS : BackpackSizes.DEFAULT_ROWS;
            this.rigInventory = new RigInventory(cols, rows);
            return;
        }

        this.rigInventory = loadRigInv(equipped);
    }

    private RigInventory loadRigInv(ItemStack item) {
        if (item.getTag() != null && item.getTag().contains("TarkovRigInventory")) {
            return RigInventory.unwrapFromNBT(item.getTag().getCompound("TarkovRigInventory"));
        }
        int cols = mode == Mode.RIG ? RigSizes.getCols(item) : BackpackSizes.getCols(item);
        int rows = mode == Mode.RIG ? RigSizes.getRows(item) : BackpackSizes.getRows(item);
        return new RigInventory(cols, rows);
    }

    private void saveToItem() {
        ItemStack equipped = getEquippedItem();
        if (equipped.isEmpty()) return;

        equipped.getOrCreateTag().put("TarkovRigInventory", rigInventory.serializeNBT());
        // Re-set the item to trigger NBT sync to client
        setEquippedItem(equipped);
    }

    private ItemStack getEquippedItem() {
        if (mode == Mode.RIG) {
            // Check capability FIRST — prevents stale Curios "body" from keeping
            // the rig detected as equipped after the player has taken it out.
            ItemStack capRig = player.getCapability(com.tarkovinventory.capability.ModCapabilities.PLAYER_EQUIPMENT)
                    .map(cap -> cap.getSlot(IPlayerEquipment.SLOT_RIG))
                    .orElse(ItemStack.EMPTY);
            if (!capRig.isEmpty()) return capRig;
            // Curios "body" fallback — for MM rigs placed directly via Curios slot
            if (CuriosCompat.isLoaded()) {
                ItemStack cur = CuriosCompat.getSlotItem(player, "body", 0);
                if (!cur.isEmpty() && com.tarkovinventory.inventory.RigSizes.isRegistered(cur)) return cur;
            }
            return ItemStack.EMPTY;
        } else {
            // BACKPACK: use ONLY our capability slot as the source of truth.
            // Curios "back" is intentionally NOT checked here because:
            //   1. Stale Curios data (e.g. from a gun placed in Curios UI) would
            //      falsely activate the backpack grid.
            //   2. After unequipping, Curios "back" may not be cleared, causing
            //      the backpack to appear still equipped.
            // We sync TO Curios for visuals; reads come from our capability only.
            ItemStack capBack = player.getCapability(com.tarkovinventory.capability.ModCapabilities.PLAYER_EQUIPMENT)
                    .map(cap -> cap.getSlot(IPlayerEquipment.SLOT_ON_BACK))
                    .orElse(ItemStack.EMPTY);
            // Extra guard: only treat item as backpack if registered in BackpackSizes
            if (!capBack.isEmpty() && com.tarkovinventory.inventory.BackpackSizes.isRegistered(capBack))
                return capBack;
            return ItemStack.EMPTY;
        }
    }

    private void setEquippedItem(ItemStack stack) {
        if (mode == Mode.RIG) {
            if (CuriosCompat.isLoaded() && !CuriosCompat.getSlotItem(player, "body", 0).isEmpty()) {
                CuriosCompat.setSlot(player, "body", 0, stack);
            } else {
                player.getCapability(com.tarkovinventory.capability.ModCapabilities.PLAYER_EQUIPMENT)
                        .ifPresent(cap -> cap.setSlot(IPlayerEquipment.SLOT_RIG, stack));
            }
        } else {
            player.getCapability(com.tarkovinventory.capability.ModCapabilities.PLAYER_EQUIPMENT)
                    .ifPresent(cap -> cap.setSlot(IPlayerEquipment.SLOT_ON_BACK, stack));
            // Always mirror to Curios (including clear) so Curios stays in sync
            // and cannot push stale data back into our capability.
            if (CuriosCompat.isLoaded()) {
                CuriosCompat.setSlot(player, "back", 0, stack);
            }
        }
    }

    // ── Container implementation ────────────────────────────────────

    @Override
    public int getContainerSize() {
        return rigInventory != null ? rigInventory.getSlots() : 0;
    }

    @Override
    public boolean isEmpty() {
        if (rigInventory == null) return true;
        for (int i = 0; i < rigInventory.getSlots(); i++) {
            if (!rigInventory.getItem(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (rigInventory == null || !rigInventory.isValid(slot)) return ItemStack.EMPTY;
        return rigInventory.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (rigInventory == null || !rigInventory.isValid(slot)) return ItemStack.EMPTY;
        ItemStack taken = rigInventory.extractItem(slot, amount);
        if (!taken.isEmpty()) {
            saveToItem();
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (rigInventory == null || !rigInventory.isValid(slot)) return ItemStack.EMPTY;
        return rigInventory.extractItem(slot, 64);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (rigInventory == null || !rigInventory.isValid(slot)) return;
        rigInventory.setItem(slot, stack);
        saveToItem();
        setChanged();
    }

    @Override
    public void setChanged() {
        changeCount++;
    }

    public int getChangeCount() {
        return changeCount;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        if (rigInventory == null) return;
        for (int i = 0; i < rigInventory.getSlots(); i++) {
            rigInventory.setItem(i, ItemStack.EMPTY);
        }
        saveToItem();
        setChanged();
    }

    // ── Accessors ───────────────────────────────────────────────────

    public RigInventory getRigInventory() {
        return rigInventory;
    }

    public boolean isItemEquipped() {
        return !getEquippedItem().isEmpty();
    }

    public Mode getMode() {
        return mode;
    }

    public int getCols() {
        return rigInventory != null ? rigInventory.getCols() : 0;
    }

    public int getRows() {
        return rigInventory != null ? rigInventory.getRows() : 0;
    }
}
