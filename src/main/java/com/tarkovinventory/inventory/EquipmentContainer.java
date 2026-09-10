package com.tarkovinventory.inventory;

import com.tarkovinventory.capability.IPlayerEquipment;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Container combining vanilla armor slots and Tarkov custom equipment slots.
 *
 * Slot layout:
 *   [0..3] = vanilla armor (head=0, chest=1, legs=2, feet=3)
 *   [4..10] = Tarkov custom (face, ear, rig, pants, knees, armband, backpack)
 *
 * Vanilla armor is stored in the player's armor inventory (indices 5-8).
 * Custom equipment is stored in the capability (indices 0-6).
 */
public class EquipmentContainer implements Container {

    private final Player player;
    private final IPlayerEquipment cap;
    private int changeCount;

    // Vanilla armor indices in player.armor (0=head, 1=chest, 2=legs, 3=feet)
    // These correspond to player.armorContents[5..8] in the main inventory
    private static final int ARMOR_HEAD  = 0;
    private static final int ARMOR_CHEST = 1;
    private static final int ARMOR_LEGS  = 2;
    private static final int ARMOR_FEET  = 3;
    private static final int ARMOR_SLOTS = 4;

    public EquipmentContainer(Player player, IPlayerEquipment cap) {
        this.player = player;
        this.cap = cap;
    }

    @Override
    public int getContainerSize() {
        return ARMOR_SLOTS + IPlayerEquipment.SLOT_COUNT; // 4 + 7 = 11
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            if (!player.getInventory().getArmor(i).isEmpty()) return false;
        }
        for (int i = 0; i < IPlayerEquipment.SLOT_COUNT; i++) {
            if (!cap.getSlot(i).isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < ARMOR_SLOTS) return player.getInventory().getArmor(slot);
        return cap.getSlot(slot - ARMOR_SLOTS);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack current = getItem(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;

        ItemStack taken;
        if (current.getCount() <= amount) {
            taken = current.copy();
            setItem(slot, ItemStack.EMPTY);
        } else {
            taken = current.copy();
            taken.setCount(amount);
            current.shrink(amount);
            setItem(slot, current);
        }
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack current = getItem(slot);
        setItem(slot, ItemStack.EMPTY);
        return current;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < ARMOR_SLOTS) {
            player.getInventory().armor.set(slot, stack);
        } else {
            cap.setSlot(slot - ARMOR_SLOTS, stack);
        }
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
        for (int i = 0; i < ARMOR_SLOTS; i++) {
            player.getInventory().armor.set(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < IPlayerEquipment.SLOT_COUNT; i++) {
            cap.setSlot(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    // ── Accessors ─────────────────────────────────────────────────

    public int getChangeCount() {
        return changeCount;
    }

    public IPlayerEquipment getCapability() {
        return cap;
    }

    public Player getPlayer() {
        return player;
    }
}
