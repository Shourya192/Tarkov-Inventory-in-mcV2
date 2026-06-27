package com.tarkovinventory.client.screen.modules;

import net.minecraft.world.item.ItemStack;

/**
 * Pure client-side drag state holder.
 * No logic, no networking, just UI state.
 */
public class DragState {

    private ItemStack dragging = ItemStack.EMPTY;
    private int offsetX;
    private int offsetY;
    private boolean active;

    public ItemStack getDragging() {
        return dragging;
    }

    public void setDragging(ItemStack stack) {
        this.dragging = stack == null ? ItemStack.EMPTY : stack;
        this.active = !this.dragging.isEmpty();
    }

    public boolean isDragging() {
        return active && !dragging.isEmpty();
    }

    public void clear() {
        this.dragging = ItemStack.EMPTY;
        this.active = false;
        this.offsetX = 0;
        this.offsetY = 0;
    }

    public void setOffset(int x, int y) {
        this.offsetX = x;
        this.offsetY = y;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }
}
