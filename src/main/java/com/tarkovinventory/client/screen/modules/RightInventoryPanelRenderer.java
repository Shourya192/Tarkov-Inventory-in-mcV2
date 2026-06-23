package com.tarkovinventory.client.screen.modules;

import net.minecraft.client.gui.GuiGraphics;

public class RightInventoryPanelRenderer {

    private static final int CELL = 18;

    public void render(GuiGraphics g, int left, int top) {

        int width = 10 * CELL;
        int lootRows = 8;
        int vicinityRows = 4;

        int lootHeight = lootRows * CELL;
        int vicinityHeight = vicinityRows * CELL;

        int totalHeight = lootHeight + vicinityHeight + 40;

        // Outer frame
        g.fill(
                left - 8,
                top - 8,
                left + width + 8,
                top + totalHeight,
                0xFF0B0B0B
        );

        // Inner background
        g.fill(
                left - 4,
                top - 4,
                left + width + 4,
                top + totalHeight - 4,
                0xFF151515
        );

        // TOP GRID
        int lootGridY = top + 20;

        for (int row = 0; row < lootRows; row++) {
            for (int col = 0; col < 10; col++) {

                int x = left + col * CELL;
                int y = lootGridY + row * CELL;

                g.fill(
                        x,
                        y,
                        x + CELL - 1,
                        y + CELL - 1,
                        0xFF262626
                );
            }
        }

        // BOTTOM GRID (NO GAP)
        int vicinityGridY = lootGridY + lootHeight;

        for (int row = 0; row < vicinityRows; row++) {
            for (int col = 0; col < 10; col++) {

                int x = left + col * CELL;
                int y = vicinityGridY + row * CELL;

                g.fill(
                        x,
                        y,
                        x + CELL - 1,
                        y + CELL - 1,
                        0xFF262626
                );
            }
        }
    }
}
