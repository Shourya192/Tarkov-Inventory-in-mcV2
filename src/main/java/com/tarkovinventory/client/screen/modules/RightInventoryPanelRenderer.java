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
              + 14 + 12 + CELL             // weapons row separator + label + cells
              + 16 + 12 + CELL;            // pocket separator + pocket label + pocket cells
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
                       boolean corpseHasRig, boolean corpseHasBp,
                       boolean[] lootHasItem) {

        if (!isCorpse && !hasLootSource) return;

        // searchCurrentSlot == -1 means all slots searched (or nothing to search)
        boolean searchDone = searchCurrentSlot < 0;
        int width = panelWidth();
        // Always reserve the rig/backpack grid space (matching the baked slot positions),
        // even after the gear item is taken — prevents phantom boxes below the panel.
        int cRigRows  = isCorpse ? corpseRigRows  : 0;
        int cGridRows = isCorpse ? corpseGridRows : 0;
        // Container height: only as many rows as needed to show the items (min 3 rows)
        int containerRows = Math.max(3, (int) Math.ceil(activeLootCount / (double) COLS));
        containerRows = Math.min(containerRows, LOOT_ROWS);
        int height = isCorpse ? corpseHeight(cRigRows, cGridRows)
                              : HEADER_H + containerRows * CELL + 8;

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
            // 8 gear slots on the first row
            for (int i = 0; i < 8; i++) {
                int cx = left + i * CELL, cy = gearY;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, searchDone ? 0xFF3A3A2E : 0xFF252518);
            }
            // ── WEAPONS row (PRIMARY / SECONDARY) ────────────────────
            int weapY = gearY + CELL + 14;
            g.fill(left, weapY - 6, left + width, weapY - 5, 0xFF2A2A20);
            g.drawString(mc.font, "WEAPONS", left, weapY - 12, 0xFF888866, false);
            for (int i = 0; i < 2; i++) {
                int cx = left + i * CELL, cy = weapY;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, searchDone ? 0xFF3A3A2E : 0xFF252518);
            }
            // ── POCKETS row ──────────────────────────────────────────
            int pocketY = weapY + CELL + 16; // extra gap
            // Horizontal divider before POCKETS
            g.fill(left, pocketY - 10, left + width, pocketY - 9, 0xFF2A2A20);
            g.drawString(mc.font, "POCKETS", left, pocketY - 8, 0xFF666655, false);
            for (int i = 0; i < 7; i++) {
                int cx = left + i * CELL, cy = pocketY + LABEL_H - 2;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, searchDone ? 0xFF1C1C14 : 0xFF141410);
            }
            int pocketCellY = pocketY + LABEL_H - 2;
            // ── RIG grid ─────────────────────────────────────────────
            // Always reserve the rig area (label + grid) so the backpack grid
            // stays at a FIXED position matching the baked slots — even when the
            // rig item has been taken. The grid cells are only drawn when present.
            int rigY = pocketCellY + CELL + 16;
            int rigRowsReserve = corpseRigRows;
            g.fill(left, rigY - 10, left + width, rigY - 9, 0xFF2A2A20);
            g.drawString(mc.font, "RIG", left, rigY - 8, 0xFF666655, false);
            int rigCellY = rigY + LABEL_H - 2;
            if (corpseHasRig) {
                for (int row = 0; row < corpseRigRows; row++)
                    for (int col = 0; col < Math.min(corpseRigCols, COLS); col++)
                        g.fill(left + col*CELL, rigCellY + row*CELL,
                               left + col*CELL + CELL-1, rigCellY + row*CELL + CELL-1,
                               searchDone ? 0xFF1C1C14 : 0xFF141410);
            }
            // ── BACKPACK grid ─────────────────────────────────────────
            // Fixed position: always after the reserved rig area.
            int bpBase = rigCellY + rigRowsReserve * CELL + 16;
            g.fill(left, bpBase - 10, left + width, bpBase - 9, 0xFF2A2A20);
            g.drawString(mc.font, "BACKPACK", left, bpBase - 8, 0xFF666655, false);
            if (corpseHasBp) {
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
            int rowsToDraw = Math.min(LOOT_ROWS, Math.max(3, (int) Math.ceil(activeLootCount / (double) COLS)));
            for (int row = 0; row < rowsToDraw; row++) {
                for (int col = 0; col < COLS; col++) {
                    int idx = row * COLS + col;
                    int cx  = left + col * CELL, cy = gridY + row * CELL;
                    int color;
                    if (idx >= activeLootCount)      color = 0xFF1A1A14; // beyond container — empty
                    else if (lootHasItem != null && idx < lootHasItem.length && !lootHasItem[idx])
                                                     color = 0xFF1C1C14; // empty slot — neutral
                    else if (!searchDone)            color = 0xFF252518; // has item, still locked
                    else                             color = 0xFF3A3A2E; // has item, available
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
