package com.tarkovinventory.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.item.ItemStack;

public class RigInventory {

    private ItemStack[] items;
    private int cols;
    private int rows;

    public RigInventory(int cols, int rows) {
        this.cols = Math.max(1, cols);
        this.rows = Math.max(1, rows);

        this.items = new ItemStack[getSlots()];

        for (int i = 0; i < items.length; i++) {
            items[i] = ItemStack.EMPTY;
        }
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public int getSlots() {
        return cols * rows;
    }

    public boolean isValid(int slot) {
        return slot >= 0 && slot < items.length;
    }

    public ItemStack getItem(int slot) {
        if (!isValid(slot)) {
            return ItemStack.EMPTY;
        }

        return items[slot];
    }

    public ItemStack setItem(int slot, ItemStack stack) {
        if (!isValid(slot)) {
            return stack;
        }

        ItemStack old = items[slot];
        items[slot] = stack;
        return old;
    }

    public ItemStack insertItem(int slot, ItemStack stack) {
        if (!isValid(slot) || stack.isEmpty()) {
            return stack;
        }

        ItemStack existing = items[slot];

        if (existing.isEmpty()) {
            items[slot] = stack.copy();
            return ItemStack.EMPTY;
        }

        if (ItemStack.isSameItemSameTags(existing, stack)) {
            int space = existing.getMaxStackSize() - existing.getCount();
            int move = Math.min(space, stack.getCount());

            existing.grow(move);

            ItemStack leftover = stack.copy();
            leftover.shrink(move);

            return leftover;
        }

        return stack;
    }

    public ItemStack extractItem(int slot, int amount) {
        if (!isValid(slot) || amount <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack existing = items[slot];

        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int extracted = Math.min(amount, existing.getCount());

        ItemStack result = existing.copy();
        result.setCount(extracted);

        existing.shrink(extracted);

        if (existing.getCount() <= 0) {
            items[slot] = ItemStack.EMPTY;
        }

        return result;
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (simulate) {
            ItemStack stack = getItem(slot);

            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }

            ItemStack copy = stack.copy();
            copy.setCount(Math.min(amount, copy.getCount()));

            return copy;
        }

        return extractItem(slot, amount);
    }

    public void setSize(int cols, int rows) {
        int newSize = Math.max(1, cols) * Math.max(1, rows);

        ItemStack[] newItems = new ItemStack[newSize];

        for (int i = 0; i < newSize; i++) {
            newItems[i] = ItemStack.EMPTY;
        }

        int copyCount = Math.min(items.length, newSize);

        System.arraycopy(items, 0, newItems, 0, copyCount);

        this.cols = cols;
        this.rows = rows;
        this.items = newItems;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();

        for (int i = 0; i < items.length; i++) {
            if (!items[i].isEmpty()) {
                CompoundTag entry = new CompoundTag();

                entry.putByte("Slot", (byte) i);
                items[i].save(entry);

                list.add(entry);
            }
        }

        tag.put("Items", list);
        tag.putInt("Cols", cols);
        tag.putInt("Rows", rows);

        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        this.cols = Math.max(1, tag.getInt("Cols"));
        this.rows = Math.max(1, tag.getInt("Rows"));

        int size = cols * rows;

        this.items = new ItemStack[size];

        for (int i = 0; i < size; i++) {
            items[i] = ItemStack.EMPTY;
        }

        ListTag list = tag.getList("Items", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            int slot = entry.getByte("Slot") & 255;

            if (slot >= 0 && slot < items.length) {
                items[slot] = ItemStack.of(entry);
            }
        }
    }

    public static RigInventory unwrapFromNBT(CompoundTag tag) {
        int cols = Math.max(1, tag.getInt("Cols"));
        int rows = Math.max(1, tag.getInt("Rows"));

        if (cols <= 0) cols = 3;
        if (rows <= 0) rows = 3;

        RigInventory inv = new RigInventory(cols, rows);
        inv.deserializeNBT(tag);

        return inv;
    }
}
