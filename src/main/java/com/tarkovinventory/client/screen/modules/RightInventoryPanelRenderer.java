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

    public static int corpseHeight(int rigRows, int gridRows) {
        int h = HEADER_H + 4 + 12 + CELL   // header + gear gap + gear label + gear cells
              + 16 + 12 + CELL;              // pocket separator + pocket label + pocket cells
        if (rigRows  > 0) h += 16 + 12 + rigRows  * CELL;
        if (gridRows > 0) h += 16 + 12 + gridRows * CELL;
        return h + 8;
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
                       int searchCurrentSlot, int searchRequired,
                       boolean corpseHasRig, boolean corpseHasBp) {

        if (!isCorpse && !hasLootSource) return;

        // searchCurrentSlot == -1 means all slots searched (or nothing to search)
        boolean searchDone = searchCurrentSlot < 0;
        int width = panelWidth();
        // Corpse height: account for rig/backpack grids only if their items are present
        int cRigRows  = (isCorpse && corpseHasRig) ? corpseRigRows  : 0;
        int cGridRows = (isCorpse && corpseHasBp)  ? corpseGridRows : 0;
        int height = isCorpse ? corpseHeight(cRigRows, cGridRows) : panelHeight();

        // Panel background
        g.fill(left - 8, top - 8, left + width + 8, top + height + 4, 0xFF0B0B0B);
        g.fill(left - 4, top - 4, left + width + 4, top + height,     0xFF151515);

        // Header
        String label = lootOwner.isEmpty() ? (isCorpse ? "CORPSE" : "LOOT") : lootOwner;
        g.drawString(mc.font, label, left, top + 2, 0xFFCCCC88, false);

        if (!searchDone) {
            String txt = "SEARCHING...";
            int tw = mc.font.width(txt);
            g.drawString(mc.font, txt, left + width - tw, top + 2, 0xFF888822, false);
        } else if (activeLootCount > 0) {
            String btn = "[LOOT ALL]";
            g.drawString(mc.font, btn, left + width - mc.font.width(btn), top + 2, 0xFF888855, false);
        }

        if (isCorpse) {
            // ── GEAR row ─────────────────────────────────────────────
            int gearY = lootGridTop(top) + 4; // extra gap after header
            // Horizontal divider before GEAR
            g.fill(left, gearY - 6, left + width, gearY - 5, 0xFF2A2A20);
            g.drawString(mc.font, "GEAR", left, gearY - 12, 0xFF888866, false);
            for (int i = 0; i < 8; i++) {
                int cx = left + i * CELL, cy = gearY;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, searchDone ? 0xFF3A3A2E : 0xFF252518);
            }
            // ── POCKETS row ──────────────────────────────────────────
            int pocketY = gearY + CELL + 16; // extra gap
            // Horizontal divider before POCKETS
            g.fill(left, pocketY - 10, left + width, pocketY - 9, 0xFF2A2A20);
            g.drawString(mc.font, "POCKETS", left, pocketY - 8, 0xFF666655, false);
            for (int i = 0; i < 7; i++) {
                int cx = left + i * CELL, cy = pocketY + LABEL_H - 2;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, searchDone ? 0xFF1C1C14 : 0xFF141410);
            }
            int pocketCellY = pocketY + LABEL_H - 2;
            // ── RIG grid ─────────────────────────────────────────────
            int rigY = pocketCellY + CELL + 16;
            if (corpseHasRig) {
                g.fill(left, rigY - 10, left + width, rigY - 9, 0xFF2A2A20);
                g.drawString(mc.font, "RIG", left, rigY - 8, 0xFF666655, false);
                int rigCellY = rigY + LABEL_H - 2;
                for (int row = 0; row < corpseRigRows; row++)
                    for (int col = 0; col < Math.min(corpseRigCols, COLS); col++)
                        g.fill(left + col*CELL, rigCellY + row*CELL,
                               left + col*CELL + CELL-1, rigCellY + row*CELL + CELL-1,
                               searchDone ? 0xFF1C1C14 : 0xFF141410);
            }
            // ── BACKPACK grid ─────────────────────────────────────────
            int bpBase = corpseHasRig
                    ? rigY + LABEL_H - 2 + corpseRigRows * CELL + 16
                    : pocketCellY + CELL + 16;
            if (corpseHasBp) {
                g.fill(left, bpBase - 10, left + width, bpBase - 9, 0xFF2A2A20);
                g.drawString(mc.font, "BACKPACK", left, bpBase - 8, 0xFF666655, false);
                int bpCellY = bpBase + LABEL_H - 2;
                for (int row = 0; row < corpseGridRows; row++)
                    for (int col = 0; col < Math.min(corpseGridCols, COLS); col++)
                        g.fill(left + col*CELL, bpCellY + row*CELL,
                               left + col*CELL + CELL-1, bpCellY + row*CELL + CELL-1,
                               searchDone ? 0xFF1C1C14 : 0xFF141410);
            }
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
