package com.tarkovinventory.client.screen.layout;

import net.minecraft.client.gui.GuiGraphics;

public class Panel {

    public final int x, y, w, h;

    public Panel(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void draw(GuiGraphics g) {
        g.fill(x, y, x + w, y + h, 0xFF141414);
        g.fill(x, y, x + w, y + 18, 0xFF1C1C1C);
    }
}
