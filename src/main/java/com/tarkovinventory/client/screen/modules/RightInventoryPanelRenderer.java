package com.tarkovinventory.client.screen.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Right-panel renderer.
 *
 * Container mode (isCorpse=false):
 *   Flat 8×10 loot grid. Only shown when activeLootCount > 0.
 *
 * Corpse mode (isCorpse=true):
 *   Structured view matching the dead player's inventory layout:
 *     POCKETS — 7 slots in one row
 *     RIG     — variable grid (corpseRigCols × corpseRigRows)
 *     BACKPACK — variable grid (corpseGridCols × corpseGridRows)
 *   All as real Forge slots → full drag-and-drop between your inventory and theirs.
 */
public class RightInventoryPanelRenderer {

    public static final int COLS      = 10;
    public static final int LOOT_ROWS = 8;
    public static final int CELL      = 18;
    public static final int HEADER_H  = 14;
    private static final int SECTION_GAP = 8;
    private static final int LABEL_H     = 14;

    public static int panelWidth() { return COLS * CELL; }

    /** Panel height for a flat container loot grid. */
    public static int panelHeight() { return HEADER_H + LOOT_ROWS * CELL + 8; }

    /** Panel height for a structured corpse view. */
    public static int corpseHeight(int rigRows, int gridRows) {
        // HEADER + GEAR row + POCKETS + RIG + BACKPACK
        return HEADER_H + CELL + SECTION_GAP + LABEL_H    // gear
                + CELL + SECTION_GAP + LABEL_H             // pockets
                + rigRows * CELL + SECTION_GAP + LABEL_H  // rig
                + gridRows * CELL + 8;                     // backpack
    }

    public static int lootGridTop(int panelTop) { return panelTop + HEADER_H; }

    private final Minecraft mc = Minecraft.getInstance();

    /**
     * Draw background + section labels. Items are drawn by Forge on top.
     *
     * @param activeLootCount  flat loot count (container mode), or 295 for corpse
     * @param lootOwner        name for the panel header
     * @param isCorpse         true → structured corpse view
     * @param corpseRigCols/Rows   rig grid dimensions
     * @param corpseGridCols/Rows  backpack grid dimensions
     */
    public void render(GuiGraphics g, int left, int top,
                       int activeLootCount, String lootOwner, boolean isCorpse,
                       boolean hasLootSource,
                       int corpseRigCols, int corpseRigRows,
                       int corpseGridCols, int corpseGridRows) {

        if (!isCorpse && !hasLootSource) return;

        int width  = panelWidth();
        int height = isCorpse
                ? corpseHeight(corpseRigRows, corpseGridRows)
                : panelHeight();

        // ── Panel background ──────────────────────────────────────
        g.fill(left - 8, top - 8, left + width + 8, top + height + 4, 0xFF0B0B0B);
        g.fill(left - 4, top - 4, left + width + 4, top + height,     0xFF151515);

        // ── Header ────────────────────────────────────────────────
        String label = lootOwner.isEmpty() ? (isCorpse ? "CORPSE" : "LOOT") : lootOwner;
        g.drawString(mc.font, label, left, top + 2, 0xFFCCCC88, false);
        if (activeLootCount > 0) {
            String btn = "[LOOT ALL]";
            g.drawString(mc.font, btn, left + width - mc.font.width(btn), top + 2, 0xFF888855, false);
        }

        if (isCorpse) {
            // ── GEAR row (rig item, backpack, armor x4, face, ear) ────────
            int gearY = lootGridTop(top);
            g.drawString(mc.font, "GEAR", left, gearY - LABEL_H + 2, 0xFFCCCC88, false);
            for (int i = 0; i < 8; i++) {
                int cx = left + i * CELL, cy = gearY;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF3A3A2E);
            }

            // ── POCKETS row ───────────────────────────────────────────
            int pocketY = gearY + CELL + SECTION_GAP + LABEL_H;
            g.drawString(mc.font, "POCKETS", left, pocketY - LABEL_H + 2, 0xFF888888, false);
            for (int i = 0; i < 7; i++) {
                int cx = left + i * CELL, cy = pocketY;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF1C1C14);
            }

            // ── RIG grid ──────────────────────────────────────────────
            int rigY = pocketY + CELL + SECTION_GAP + LABEL_H;
            g.drawString(mc.font, "RIG", left, rigY - LABEL_H + 2, 0xFF888888, false);
            for (int row = 0; row < corpseRigRows; row++) {
                for (int col = 0; col < Math.min(corpseRigCols, COLS); col++) {
                    int cx = left + col * CELL, cy = rigY + row * CELL;
                    g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF1C1C14);
                }
            }

            // ── BACKPACK grid ─────────────────────────────────────────
            int bpY = rigY + corpseRigRows * CELL + SECTION_GAP + LABEL_H;
            g.drawString(mc.font, "BACKPACK", left, bpY - LABEL_H + 2, 0xFF888888, false);
            for (int row = 0; row < corpseGridRows; row++) {
                for (int col = 0; col < Math.min(corpseGridCols, COLS); col++) {
                    int cx = left + col * CELL, cy = bpY + row * CELL;
                    g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, 0xFF1C1C14);
                }
            }
        } else {
            // ── Flat container loot grid ──────────────────────────
            int gridY = lootGridTop(top);
            for (int row = 0; row < LOOT_ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int idx = row * COLS + col;
                    int cx  = left + col * CELL, cy = gridY + row * CELL;
                    g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1,
                            idx < activeLootCount ? 0xFF3A3A2E : 0xFF1A1A14);
                }
            }
        }
    }

    /** True when mouse is over the "LOOT ALL" button. */
    public static boolean isLootAllButton(double mx, double my, int left, int top) {
        String btn  = "[LOOT ALL]";
        int btnW    = Minecraft.getInstance().font.width(btn);
        int btnX    = left + panelWidth() - btnW;
        return mx >= btnX && mx <= left + panelWidth() && my >= top + 2 && my <= top + 11;
    }
}
