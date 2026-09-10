package com.tarkovinventory.client.screen.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

public class EquipmentSlot {

    public final String id;
    public final EquipmentSlotType type;

    public int x1, y1, x2, y2;

    public boolean hovered;

    public ItemStack item = ItemStack.EMPTY;

    public EquipmentSlot(String id, EquipmentSlotType type, int x, int y, int width, int height) {
        this.id = id;
        this.type = type;

        this.x1 = x;
        this.y1 = y;
        this.x2 = x + width;
        this.y2 = y + height;
    }

    public EquipmentSlot(String id, EquipmentSlotType type, int x, int y, int size) {
        this(id, type, x, y, size, size);
    }

    public void render(GuiGraphics g) {
        render(g, true);
    }

    public void render(GuiGraphics g, boolean renderLocalItem) {

        int bg = hovered ? 0xFF242424 : 0xFF161616;

        g.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF000000);
        g.fill(x1, y1, x2, y2, bg);

        g.fill(x1, y1, x1 + 1, y2, 0xFF2A2A2A);
        g.fill(x1, y1, x2, y1 + 1, 0xFF3A3A3A);
        g.fill(x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xFF101010);

        // item render
        if (renderLocalItem && !item.isEmpty()) {
            Minecraft mc = Minecraft.getInstance();
            g.renderItem(item, x1 + 4, y1 + 4);
            g.renderItemDecorations(mc.font, item, x1 + 4, y1 + 4);
        }
    }

    public void renderLabel(GuiGraphics g, Minecraft mc) {
        g.drawString(mc.font, id, x1, y1 - 10, 0xFFB0B0B0, false);
    }

    public boolean isMouseOver(double mx, double my) {
        return mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
    }
}
