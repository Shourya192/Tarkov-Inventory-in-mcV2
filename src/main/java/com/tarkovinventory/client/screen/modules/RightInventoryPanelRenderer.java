package com.tarkovinventory.client.screen.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Loot panel — only drawn when a corpse or container is open (activeLootCount > 0).
 * Items in the 80 Forge-backed slots are rendered by AbstractContainerScreen;
 * this class draws only the panel background and cell backgrounds beneath them.
 */
public class RightInventoryPanelRenderer {

    public static final int COLS      = 10;
    public static final int LOOT_ROWS = 8;
    public static final int CELL      = 18;
    public static final int HEADER_H  = 14;

    public static int panelWidth()              { return COLS * CELL; }
    public static int panelHeight()             { return HEADER_H + LOOT_ROWS * CELL + 8; }
    public static int lootGridTop(int panelTop) { return panelTop + HEADER_H; }

    private final Minecraft mc = Minecraft.getInstance();

    /**
     * Renders the loot panel. Does nothing when activeLootCount == 0 so the
     * panel is invisible until the player opens a corpse or container.
     */
    public void render(GuiGraphics g, int left, int top,
                       int activeLootCount, String lootOwner) {
        if (activeLootCount <= 0) return;

        int width     = panelWidth();
        int height    = panelHeight();
        int lootGridY = lootGridTop(top);

        // Background
        g.fill(left - 8, top - 8, left + width + 8, top + height + 4, 0xFF0B0B0B);
        g.fill(left - 4, top - 4, left + width + 4, top + height,     0xFF151515);

        // Header
        String label = lootOwner.isEmpty() ? "LOOT" : lootOwner;
        g.drawString(mc.font, label, left, top + 2, 0xFFCCCC88, false);
        String btn = "[LOOT ALL]";
        g.drawString(mc.font, btn, left + width - mc.font.width(btn), top + 2, 0xFF888855, false);

        // Cell backgrounds (Forge renders items on top)
        for (int row = 0; row < LOOT_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col;
                int cx  = left + col * CELL;
                int cy  = lootGridY + row * CELL;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1,
                        idx < activeLootCount ? 0xFF1C1C14 : 0xFF111110);
            }
        }
    }

    /** True when mouse is over the "LOOT ALL" button. */
    public static boolean isLootAllButton(double mx, double my, int left, int top) {
        String btn = "[LOOT ALL]";
        int btnW = Minecraft.getInstance().font.width(btn);
        int btnX = left + panelWidth() - btnW;
        return mx >= btnX && mx <= left + panelWidth() && my >= top + 2 && my <= top + 11;
    }
}
