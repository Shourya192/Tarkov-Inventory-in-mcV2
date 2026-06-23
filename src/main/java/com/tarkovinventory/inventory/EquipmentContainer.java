package com.tarkovinventory.inventory;

import com.tarkovinventory.capability.IPlayerEquipment;
import net.minecraft.core.NonNullList;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Container wrapper over the player equipment capability.
 * Provides a standard Container interface so Forge slots can read/write
 * equipment slots (earpiece, armband, on-back) with automatic change
 * detection for multiplayer sync.
 *
 * Slot indices:
 *   0 = SLOT_EARPIECE
 *   1 = SLOT_ARMBAND
 *   2 = SLOT_ON_BACK (backpack)
 */
public class EquipmentContainer implements Container {

    private final IPlayerEquipment cap;
    private final int size;
    private int changeCount;

    public EquipmentContainer(IPlayerEquipment cap) {
        this.cap = cap;
        this.size = IPlayerEquipment.SLOT_COUNT; // 3
    }

    @Override
    public int getContainerSize() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < size; i++) {
            if (!cap.getSlot(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return cap.getSlot(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack current = cap.getSlot(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;

        ItemStack taken;
        if (current.getCount() <= amount) {
            taken = current.copy();
            cap.setSlot(slot, ItemStack.EMPTY);
        } else {
            taken = current.copy();
            taken.setCount(amount);
            current.shrink(amount);
            cap.setSlot(slot, current);
        }
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = cap.getSlot(slot);
        cap.setSlot(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        cap.setSlot(slot, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
        changeCount++;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < size; i++) {
            cap.setSlot(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    // ── Forge Container change tracking ───────────────────────────

    public int getChangeCount() {
        return changeCount;
    }

    public IPlayerEquipment getCapability() {
        return cap;
    }
}
