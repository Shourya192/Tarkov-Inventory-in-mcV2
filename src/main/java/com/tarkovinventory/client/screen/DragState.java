package com.tarkovinventory.client.screen;

import net.minecraft.world.item.ItemStack;

public class DragState {

    private boolean dragging;
    private ItemStack stack = ItemStack.EMPTY;

    public void start(ItemStack s) {
        this.dragging = true;
        this.stack = s;
    }

    public void stop() {
        this.dragging = false;
        this.stack = ItemStack.EMPTY;
    }

    public boolean isDragging() {
        return dragging;
    }

    public ItemStack getDragging() {
        return stack;
    }
}
