package com.tarkovinventory.inventory;

import com.tarkovinventory.client.screen.modules.EquipmentSlotType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Custom Forge Slot for the Tarkov inventory system.
 *
 * Extends the standard Slot with:
 * - Equipment type validation (via EquipmentSlotTypeValidator)
 * - Tarkov-style visual rendering (dark slot backgrounds, hover effects)
 * - Optional disabled state (for rig/backpack slots when no item is equipped)
 */
public class TarkovSlot extends Slot {

    private final EquipmentSlotType equipmentType;
    private boolean enabled = true;
    private boolean hovered;

    public TarkovSlot(Container container, int index, int x, int y, EquipmentSlotType type) {
        super(container, index, x, y);
        this.equipmentType = type;
    }

    /** Standard slot with no equipment validation. */
    public TarkovSlot(Container container, int index, int x, int y) {
        this(container, index, x, y, EquipmentSlotType.UNKNOWN);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        if (!enabled) return false;
        if (equipmentType == EquipmentSlotType.UNKNOWN) return true;
        return EquipmentSlotTypeValidator.isValid(stack, equipmentType);
    }

    @Override
    public boolean isActive() {
        return enabled;
    }

    // ── Rendering ───────────────────────────────────────────────────

    /**
     * Render the Tarkov-style slot background.
     * Called from the screen's renderBg method.
     */
    public void renderTarkovBackground(GuiGraphics g, int x, int y) {
        int bgColor = hovered ? 0xFF2A2A2A : 0xFF161616;

        // Outer border
        g.fill(x - 1, y - 1, x + 18, y + 18, 0xFF000000);
        // Main fill
        g.fill(x, y, x + 17, y + 17, bgColor);

        // Inner highlight (top-left light, inner dark)
        g.fill(x, y, x + 1, y + 17, 0xFF2A2A2A);
        g.fill(x, y, x + 17, y + 1, 0xFF3A3A3A);
        g.fill(x + 1, y + 1, x + 16, y + 16, 0xFF101010);
    }

    /**
     * Render the item in this slot with Tarkov styling.
     */
    public void renderTarkovItem(GuiGraphics g, int x, int y) {
        ItemStack stack = getItem();
        if (!stack.isEmpty()) {
            g.renderItem(stack, x + 1, y + 1);
            // Note: item decorations (count, durability) need a Minecraft instance
            // which the screen provides
        }
    }

    // ── State ───────────────────────────────────────────────────────

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setHovered(boolean hovered) {
        this.hovered = hovered;
    }

    public boolean isHovered() {
        return hovered;
    }

    public EquipmentSlotType getEquipmentType() {
        return equipmentType;
    }
}
