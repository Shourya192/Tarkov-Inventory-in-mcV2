package com.tarkovinventory.capability;

import com.tarkovinventory.inventory.GridInventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * Stores the custom Tarkov equipment slots that don't exist in vanilla.
 *
 * PRIMARY and SECONDARY weapons are stored directly on the hotbar (slots 0 and 1)
 * so they are usable in-game without any extra sync.
 * Pocket slots 0-6 are backed by hotbar slots 2-8.
 *
 * Vanilla armor (helmet/chest/legs/boots) is read directly from the player.
 */
public interface IPlayerEquipment {

    int SLOT_EARPIECE  = 0;
    int SLOT_ARMBAND   = 1;
    int SLOT_ON_BACK   = 2;   // backpack slot — item here enables the grid
    int SLOT_COUNT     = 3;

    // Equipment slots
    ItemStack getSlot(int index);
    void setSlot(int index, ItemStack stack);

    // Backpack grid (8×8)
    GridInventory getGridInventory();

    CompoundTag serializeNBT();
    void deserializeNBT(CompoundTag tag);
}
