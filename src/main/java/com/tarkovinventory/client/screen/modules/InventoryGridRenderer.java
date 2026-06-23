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

    // Hover tracking
    private int hoverGridSlot = -1;
    private int hoverRigSlot  = -1;
    private int hoverBpSlot   = -1;

    // ── Init ────────────────────────────────────────────────────────

    public void init(int left, int top, int bpCols, int bpRows) {
        init(left, top, bpCols, bpRows, 3, 3);
    }

    public void init(int left, int top, int bpCols, int bpRows, int rigCols, int rigRows) {
        this.left = left;
        this.top = top;
        this.bpCols = Math.max(1, bpCols);
        this.bpRows = Math.max(1, bpRows);
        this.rigCols = Math.max(1, rigCols);
        this.rigRows = Math.max(1, rigRows);
    }

    public void setRigDimensions(int cols, int rows) {
        this.rigCols = Math.max(1, cols);
        this.rigRows = Math.max(1, rows);
    }

    public void setBackpackDimensions(int cols, int rows) {
        this.bpCols = Math.max(1, cols);
        this.bpRows = Math.max(1, rows);
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

    /** Backpack panel grid start Y. */
    public int getBackpackY() {
        return getRigGridY() + (rigRows * CELL) + SECTION_GAP + LABEL_H;
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
        int width = Math.max(8, Math.max(bpCols, rigCols)) * CELL;
        int totalHeight = getBackpackY() + (bpRows * CELL) - top;

        // Outer panel background
        g.fill(left - 16, top - 16, left + width + 16, top + totalHeight + 16, 0xFF080808);
        g.fill(left - 12, top - 12, left + width + 12, top + totalHeight + 12, 0xFF111111);
        g.fill(left - 8,  top - 8,  left + width + 8,  top + totalHeight + 8,  0xFF1A1A1A);

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
        for (int row = 0; row < rigRows; row++) {
            for (int col = 0; col < rigCols; col++) {
                int idx = row * rigCols + col;
                drawSlotCell(g, left + col * CELL, rigY + row * CELL, hoverRigSlot == idx);
            }
        }

        // ── BACKPACK GRID ───────────────────────────────────────
        int bpY = getBackpackY();
        drawSectionLabel(g, "BACKPACK", bpY - LABEL_H);
        for (int row = 0; row < bpRows; row++) {
            for (int col = 0; col < bpCols; col++) {
                int idx = row * 12 + col;
                drawSlotCell(g, left + col * CELL, bpY + row * CELL, hoverGridSlot == idx);
            }
        }
    }

    private void drawSlotCell(GuiGraphics g, int x, int y, boolean hovered) {
        int color = hovered ? 0xFF4A4A4A : 0xFF2B2B2B;
        g.fill(x, y, x + CELL - 1, y + CELL - 1, color);
    }

    private void drawSectionLabel(GuiGraphics g, String label, int y) {
        Minecraft mc = Minecraft.getInstance();
        g.drawString(mc.font, label, left, y, 0xFF808080, false);
    }

    // ── Dimensions ──────────────────────────────────────────────────

    public int getWidth() {
        return Math.max(8, Math.max(bpCols, rigCols)) * CELL;
    }

    public int getTotalHeight() {
        return getBackpackY() + (bpRows * CELL) - top;
    }

    public int getActiveCols() { return bpCols; }
    public int getActiveRows() { return bpRows; }
    public int getRigCols() { return rigCols; }
    public int getRigRows() { return rigRows; }
}
