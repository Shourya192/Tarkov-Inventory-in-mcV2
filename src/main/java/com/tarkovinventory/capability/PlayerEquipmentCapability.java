package com.tarkovinventory.capability;

import com.tarkovinventory.inventory.GridInventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public class PlayerEquipmentCapability implements IPlayerEquipment {

    private final ItemStack[]   slots         = new ItemStack[SLOT_COUNT];
    private final GridInventory gridInventory = new GridInventory();

    public PlayerEquipmentCapability() {
        for (int i = 0; i < SLOT_COUNT; i++) slots[i] = ItemStack.EMPTY;
    }

    // ── Equipment slots ───────────────────────────────────────────────

    @Override
    public ItemStack getSlot(int index) {
        return (index >= 0 && index < SLOT_COUNT) ? slots[index] : ItemStack.EMPTY;
    }

    @Override
    public void setSlot(int index, ItemStack stack) {
        if (index >= 0 && index < SLOT_COUNT)
            slots[index] = stack == null ? ItemStack.EMPTY : stack;
    }

    // ── Grid ──────────────────────────────────────────────────────────

    @Override
    public GridInventory getGridInventory() { return gridInventory; }

    // ── NBT ───────────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();

        ListTag eqList = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!slots[i].isEmpty()) {
                CompoundTag e = new CompoundTag();
                e.putInt("Slot", i);
                e.put("Item", slots[i].save(new CompoundTag()));
                eqList.add(e);
            }
        }
        tag.put("Slots", eqList);
        tag.put("Grid", gridInventory.save());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        for (int i = 0; i < SLOT_COUNT; i++) slots[i] = ItemStack.EMPTY;
        ListTag eqList = tag.getList("Slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < eqList.size(); i++) {
            CompoundTag e = eqList.getCompound(i);
            int idx = e.getInt("Slot");
            if (idx >= 0 && idx < SLOT_COUNT)
                slots[idx] = ItemStack.of(e.getCompound("Item"));
        }
        if (tag.contains("Grid")) gridInventory.load(tag.getCompound("Grid"));
    }
}
