package com.tarkovinventory.client.screen.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class RightInventoryPanelRenderer {

    public static final int COLS      = 10;
    public static final int LOOT_ROWS = 8;
    public static final int CELL      = 18;
    public static final int HEADER_H  = 14;
    static final int SECTION_GAP = 8;
    static final int LABEL_H     = 14;

    public static int panelWidth()  { return COLS * CELL; }
    public static int panelHeight() { return HEADER_H + LOOT_ROWS * CELL + 8; }

    /** Corpse panel height: header + GEAR + POCKETS + RIG + BACKPACK */
    public static int corpseHeight(int rigRows, int gridRows) {
        return HEADER_H                               // header
            + CELL + SECTION_GAP + LABEL_H           // gear row + pockets label
            + CELL + SECTION_GAP + LABEL_H           // pockets + rig label
            + rigRows  * CELL + SECTION_GAP + LABEL_H  // rig grid + bp label
            + gridRows * CELL + 8;                   // backpack grid
    }

    public static int lootGridTop(int panelTop) { return panelTop + HEADER_H; }

    private final Minecraft mc = Minecraft.getInstance();

    /**
     * @param searchElapsed   ticks elapsed searching (0 if no search)
     * @param searchRequired  ticks required total (0 if instant access)
     */
    public void render(GuiGraphics g, int left, int top,
                       int activeLootCount, String lootOwner, boolean isCorpse,
                       boolean hasLootSource,
                       int corpseRigCols, int corpseRigRows,
                       int corpseGridCols, int corpseGridRows,
                       int searchElapsed, int searchRequired) {

        if (!isCorpse && !hasLootSource) return;

        // searchDone = no slot currently being searched (all done or loot is empty)
        boolean searchDone = searchRequired == 0 || searchElapsed < 0;
        int width = panelWidth();
        int height = isCorpse ? corpseHeight(corpseRigRows, corpseGridRows) : panelHeight();

        // Panel background
        g.fill(left - 8, top - 8, left + width + 8, top + height + 4, 0xFF0B0B0B);
        g.fill(left - 4, top - 4, left + width + 4, top + height,     0xFF151515);

        // Header
        String label = lootOwner.isEmpty() ? (isCorpse ? "CORPSE" : "LOOT") : lootOwner;
        g.drawString(mc.font, label, left, top + 2, 0xFFCCCC88, false);

        if (!searchDone) {
            // Show a subtle SEARCHING indicator — the actual per-slot progress
            // is shown directly on the slot cells via the screen overlay.
            String txt = "SEARCHING...";
            int tw = mc.font.width(txt);
            g.drawString(mc.font, txt, left + width - tw, top + 2, 0xFF888822, false);
        } else if (activeLootCount > 0) {
            String btn = "[LOOT ALL]";
            g.drawString(mc.font, btn, left + width - mc.font.width(btn), top + 2, 0xFF888855, false);
        }

        if (isCorpse) {
            // ── GEAR row ─────────────────────────────────────────────
            int gearY = lootGridTop(top);
            g.drawString(mc.font, "GEAR", left, gearY - LABEL_H + 2, 0xFFCCCC88, false);
            for (int i = 0; i < 8; i++) {
                int cx = left + i * CELL, cy = gearY;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, searchDone ? 0xFF3A3A2E : 0xFF252518);
            }
            // ── POCKETS row ──────────────────────────────────────────
            int pocketY = gearY + CELL + SECTION_GAP + LABEL_H;
            g.drawString(mc.font, "POCKETS", left, pocketY - LABEL_H + 2, 0xFF888888, false);
            for (int i = 0; i < 7; i++) {
                int cx = left + i * CELL, cy = pocketY;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, searchDone ? 0xFF1C1C14 : 0xFF141410);
            }
            // ── RIG grid ─────────────────────────────────────────────
            int rigY = pocketY + CELL + SECTION_GAP + LABEL_H;
            g.drawString(mc.font, "RIG", left, rigY - LABEL_H + 2, 0xFF888888, false);
            for (int row = 0; row < corpseRigRows; row++)
                for (int col = 0; col < Math.min(corpseRigCols, COLS); col++)
                    g.fill(left + col*CELL, rigY + row*CELL,
                           left + col*CELL + CELL-1, rigY + row*CELL + CELL-1,
                           searchDone ? 0xFF1C1C14 : 0xFF141410);
            // ── BACKPACK grid ─────────────────────────────────────────
            int bpY = rigY + corpseRigRows * CELL + SECTION_GAP + LABEL_H;
            g.drawString(mc.font, "BACKPACK", left, bpY - LABEL_H + 2, 0xFF888888, false);
            for (int row = 0; row < corpseGridRows; row++)
                for (int col = 0; col < Math.min(corpseGridCols, COLS); col++)
                    g.fill(left + col*CELL, bpY + row*CELL,
                           left + col*CELL + CELL-1, bpY + row*CELL + CELL-1,
                           searchDone ? 0xFF1C1C14 : 0xFF141410);
        } else {
            // ── Flat container loot grid ──────────────────────────────
            int gridY = lootGridTop(top);
            for (int row = 0; row < LOOT_ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int idx = row * COLS + col;
                    int cx  = left + col * CELL, cy = gridY + row * CELL;
                    int color;
                    if (idx >= activeLootCount) color = 0xFF1A1A14;           // empty slot
                    else if (!searchDone)       color = 0xFF252518;           // locked
                    else                        color = 0xFF3A3A2E;           // available
                    g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, color);
                }
            }
        }
    }

    public static boolean isLootAllButton(double mx, double my, int left, int top) {
        String btn = "[LOOT ALL]";
        int btnW = Minecraft.getInstance().font.width(btn);
        int btnX = left + panelWidth() - btnW;
        return mx >= btnX && mx <= left + panelWidth() && my >= top + 2 && my <= top + 11;
    }
}
