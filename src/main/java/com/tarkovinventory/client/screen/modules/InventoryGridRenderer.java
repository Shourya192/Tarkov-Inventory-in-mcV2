package com.tarkovinventory.client.screen.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Renders the central column of the Tarkov inventory screen.
 *
 * Three stacked sub-panels:
 *   1. Pockets row  — 7 pocket slots (Forge hotbar slots 2-8)
 *   2. Rig panel    — dynamic grid from equipped rig item
 *   3. Backpack panel — dynamic grid from backpack grid inventory
 *
 * This class handles visual rendering and hover detection.
 * All Forge slot positioning uses the getX/getY accessors here.
 */
public class InventoryGridRenderer {

    private static final int CELL = 18;
    private static final int LABEL_H = 14;
    private static final int SECTION_GAP = 8;

    private int left;
    private int top;

    // Active dimensions for the backpack grid
    private int bpCols = 6;
    private int bpRows = 6;

    // Active dimensions for the rig panel
    private int rigCols = 3;
    private int rigRows = 3;

    // Whether items are actually equipped (controls whether grids are shown)
    private boolean rigEquipped = false;
    private boolean bpEquipped  = false;

    // Hover tracking
    private int hoverGridSlot = -1;
    private int hoverRigSlot  = -1;
    private int hoverBpSlot   = -1;

    // ── Init ────────────────────────────────────────────────────────

    public void init(int left, int top, int bpCols, int bpRows) {
        init(left, top, bpCols, bpRows, 3, 3, false, false);
    }

    public void init(int left, int top, int bpCols, int bpRows, int rigCols, int rigRows) {
        init(left, top, bpCols, bpRows, rigCols, rigRows, false, false);
    }

    public void init(int left, int top, int bpCols, int bpRows, int rigCols, int rigRows,
                     boolean rigEquipped, boolean bpEquipped) {
        this.left         = left;
        this.top          = top;
        this.bpCols       = bpCols;  // 0 = not equipped, show nothing
        this.bpRows       = bpRows;
        this.rigCols      = rigCols; // 0 = not equipped, show nothing
        this.rigRows      = rigRows;
        this.rigEquipped  = rigEquipped;
        this.bpEquipped   = bpEquipped;
    }

    public void setRigDimensions(int cols, int rows) {
        this.rigCols = cols;
        this.rigRows = rows;
    }

    public void setBackpackDimensions(int cols, int rows) {
        this.bpCols = cols;
        this.bpRows = rows;
    }

    /** Called every render frame so equipped state stays live. */
    public void setEquippedState(boolean rigEquipped, boolean bpEquipped) {
        this.rigEquipped = rigEquipped;
        this.bpEquipped  = bpEquipped;
    }

    public void updateHover(double mouseX, double mouseY) {
        hoverGridSlot = getGridSlotAt(mouseX, mouseY);
        hoverRigSlot  = getRigSlotAt(mouseX, mouseY);
        hoverBpSlot   = getBpSlotAt(mouseX, mouseY);
    }

    // ── Y-coordinate layout ─────────────────────────────────────────

    /** Pockets row Y position. */
    public int getPocketsY() { return top; }

    /** Rig panel grid start Y. */
    public int getRigGridY() {
        return getPocketsY() + CELL + SECTION_GAP + LABEL_H;
    }

    /** Backpack panel grid start Y. Uses the equipped rig's row count (or the
     *  default reserve when no rig) so it matches the baked slot positions. */
    public int getBackpackY() {
        int reservedRigRows = rigRows > 0 ? rigRows : 3; // matches menu gridBaseY
        return getRigGridY() + (reservedRigRows * CELL) + SECTION_GAP + LABEL_H;
    }

    // ── Pocket slot positions ───────────────────────────────────────

    public int getPocketX(int i) { return left + i * CELL; }
    public int getPocketY()      { return getPocketsY(); }

    // ── Rig slot positions ──────────────────────────────────────────

    public int getRigSlotX(int col)      { return left + col * CELL; }
    public int getRigSlotY(int row)      { return getRigGridY() + row * CELL; }
    public int getRigSlotIndex(int col, int row) { return row * rigCols + col; }

    // ── Backpack grid slot positions (using GridInventory.MAX_COLS stride) ──

    public int getBpSlotX(int col) { return left + col * CELL; }
    public int getBpSlotY(int row) { return getBackpackY() + row * CELL; }
    public int getBpSlotIndex(int col, int row) { return row * 12 + col; }

    // ── Hit detection ───────────────────────────────────────────────

    public int getGridSlotAt(double mouseX, double mouseY) {
        int bpY = getBackpackY();
        int col = ((int) mouseX - left) / CELL;
        int row = ((int) mouseY - bpY) / CELL;
        if (mouseX >= left && mouseY >= bpY
                && col >= 0 && col < bpCols && row >= 0 && row < bpRows) {
            return row * 12 + col;
        }
        return -1;
    }

    public int getRigSlotAt(double mouseX, double mouseY) {
        int rigY = getRigGridY();
        int col = ((int) mouseX - left) / CELL;
        int row = ((int) mouseY - rigY) / CELL;
        if (mouseX >= left && mouseY >= rigY
                && col >= 0 && col < rigCols && row >= 0 && row < rigRows) {
            return row * rigCols + col;
        }
        return -1;
    }

    public int getBpSlotAt(double mouseX, double mouseY) {
        return getGridSlotAt(mouseX, mouseY);
    }

    /** Returns which region the mouse is in: "pockets", "rig", "backpack", or null. */
    public String getRegionAt(double mouseX, double mouseY) {
        int py = getPocketsY();
        if (mouseY >= py && mouseY < py + CELL && mouseX >= left && mouseX < left + 7 * CELL)
            return "pockets";
        if (getRigSlotAt(mouseX, mouseY) >= 0) return "rig";
        if (getGridSlotAt(mouseX, mouseY) >= 0) return "backpack";
        return null;
    }

    // ── Rendering ───────────────────────────────────────────────────

    public void renderWithBackground(GuiGraphics g) {
        // Reserve space for the ACTUAL equipped rig rows (or default 3 when none),
        // matching getBackpackY() and the menu's baked slot positions exactly.
        int reservedRigRows = rigRows > 0 ? rigRows : 3;
        // Backpack reserves its real rows when equipped, else a 3-row placeholder.
        int reservedBpRows  = (bpEquipped && bpRows > 0) ? bpRows : 3;
        // Width must also account for the placeholder columns (rig=3, bp=6) when empty.
        int effRigCols = rigEquipped && rigCols > 0 ? rigCols : 3;
        int effBpCols  = bpEquipped  && bpCols > 0 ? bpCols  : 6;
        int width = Math.max(Math.max(effBpCols, effRigCols), 7) * CELL;

        int y = top;
        y += CELL;                                            // pockets row
        y += SECTION_GAP + LABEL_H + reservedRigRows * CELL;  // rig label + reserved rig area
        y += SECTION_GAP + LABEL_H + reservedBpRows * CELL;   // backpack label + reserved area
        int totalHeight = y - top;
        // Match the EQUIPMENT panel's height so the two panels are visually equal.
        // Blank space below the sections is acceptable and looks intentional.
        int minHeight = com.tarkovinventory.client.screen.modules.EquipmentPanelRenderer.PANEL_HEIGHT;
        if (totalHeight < minHeight) totalHeight = minHeight;

        // Background box — offsets match the EQUIPMENT panel (-10 outer, -6, -4)
        // so the two panels' top edges line up cleanly.
        g.fill(left - 10, top - 10, left + width + 10, top + totalHeight + 10, 0x66000000);
        g.fill(left - 6,  top - 6,  left + width + 6,  top + totalHeight + 6,  0xFF101010);
        g.fill(left - 4,  top - 4,  left + width + 4,  top + totalHeight + 4,  0xFF1A1A1A);

        render(g);
    }

    public void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();

        // ── POCKETS ─────────────────────────────────────────────
        int pocketsY = getPocketsY();
        drawSectionLabel(g, "POCKETS", pocketsY - LABEL_H);
        for (int i = 0; i < 7; i++) {
            drawSlotCell(g, left + i * CELL, pocketsY, false);
        }

        // ── RIG ─────────────────────────────────────────────────
        int rigY = getRigGridY();
        drawSectionLabel(g, "RIG", rigY - LABEL_H);
        if (rigCols > 0 && rigRows > 0 && rigEquipped) {
            // Equipped: draw the real rig grid cells
            for (int row = 0; row < rigRows; row++)
                for (int col = 0; col < rigCols; col++)
                    drawSlotCell(g, left + col * CELL, rigY + row * CELL,
                            hoverRigSlot == row * rigCols + col);
        } else {
            // Not equipped: draw a faint placeholder grid in the reserved area
            // so the layout looks intentional instead of an empty gap.
            int reservedRigRows = 3, reservedRigCols = 3;
            for (int row = 0; row < reservedRigRows; row++)
                for (int col = 0; col < reservedRigCols; col++)
                    drawEmptySlot(g, left + col * CELL, rigY + row * CELL);
        }

        // ── BACKPACK GRID ───────────────────────────────────────
        int bpY = getBackpackY();
        drawSectionLabel(g, "BACKPACK", bpY - LABEL_H);
        if (bpCols > 0 && bpRows > 0 && bpEquipped) {
            for (int row = 0; row < bpRows; row++)
                for (int col = 0; col < bpCols; col++)
                    drawSlotCell(g, left + col * CELL, bpY + row * CELL,
                            hoverGridSlot == row * 12 + col);
        } else {
            // Not equipped: faint placeholder grid (same as RIG) so the section
            // always looks intentional instead of an empty gap.
            int reservedBpRows = 3, reservedBpCols = 6;
            for (int row = 0; row < reservedBpRows; row++)
                for (int col = 0; col < reservedBpCols; col++)
                    drawEmptySlot(g, left + col * CELL, bpY + row * CELL);
        }
    }

    private void drawSlotCell(GuiGraphics g, int x, int y, boolean hovered) {
        int color = hovered ? 0xFF4A4A4A : 0xFF2B2B2B;
        g.fill(x, y, x + CELL - 1, y + CELL - 1, color);
    }

    /** Grey placeholder drawn when the slot exists but no rig/backpack is equipped. */
    private void drawEmptySlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + CELL - 1, y + CELL - 1, 0xFF1A1A16);
    }

    private void drawSectionLabel(GuiGraphics g, String label, int y) {
        Minecraft mc = Minecraft.getInstance();
        g.drawString(mc.font, label, left, y, 0xFF808080, false);
    }

    // ── Dimensions ──────────────────────────────────────────────────

    public int getWidth() {
        return Math.max(Math.max(bpCols, rigCols), 7) * CELL;
    }

    public int getTotalHeight() {
        return getBackpackY() + (bpRows * CELL) - top;
    }

    public int getActiveCols() { return bpCols; }
    public int getActiveRows() { return bpRows; }
    public int getRigCols() { return rigCols; }
    public int getRigRows() { return rigRows; }
}
