package com.tarkovinventory.client.screen.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Single unified loot panel.
 *
 * Loot items (corpse / container) fill slots first; vicinity (ground) items
 * follow immediately after in the same grid. No divider, no separate section.
 * The screen tracks how many loot items there are so it can route clicks to
 * the correct network packet.
 */
public class RightInventoryPanelRenderer {

    public static final int COLS       = 10;
    public static final int TOTAL_ROWS = 12;   // 120 slots total
    public static final int CELL       = 18;
    public static final int HEADER_H   = 14;

    public static int panelWidth()  { return COLS * CELL; }
    public static int panelHeight() { return HEADER_H + TOTAL_ROWS * CELL + 8; }
    public static int gridTop(int panelTop) { return panelTop + HEADER_H; }

    private final Minecraft mc = Minecraft.getInstance();

    /**
     * @param lootItems      items from the active corpse/container (shown first)
     * @param lootOwner      name for the header, or "" when nothing is open
     * @param vicinityStacks ground item-entity stacks (packed in after lootItems)
     * @param hoveredSlot    flat slot index under the mouse, or -1
     */
    public void render(GuiGraphics g, int left, int top,
                       List<ItemStack> lootItems, String lootOwner,
                       List<ItemStack> vicinityStacks, int hoveredSlot) {

        int width       = panelWidth();
        int totalHeight = panelHeight();
        int gridY       = gridTop(top);

        // Merge into one flat list: loot first, vicinity after
        List<ItemStack> all = new ArrayList<>(lootItems);
        all.addAll(vicinityStacks);

        // ── Background ────────────────────────────────────────────
        g.fill(left - 8, top - 8, left + width + 8, top + totalHeight + 4, 0xFF0B0B0B);
        g.fill(left - 4, top - 4, left + width + 4, top + totalHeight,     0xFF151515);

        // ── Header ────────────────────────────────────────────────
        String label = lootOwner.isEmpty() ? "LOOT" : lootOwner;
        g.drawString(mc.font, label, left, top + 2, 0xFFCCCC88, false);
        if (!lootItems.isEmpty()) {
            String btn = "[LOOT ALL]";
            g.drawString(mc.font, btn, left + width - mc.font.width(btn), top + 2, 0xFF888855, false);
        }

        // ── Single unified grid ───────────────────────────────────
        for (int row = 0; row < TOTAL_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slot = row * COLS + col;
                int cx   = left + col * CELL;
                int cy   = gridY + row * CELL;
                boolean hov = hoveredSlot == slot;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, hov ? 0xFF2E2A1E : 0xFF1C1C14);
                if (slot < all.size() && !all.get(slot).isEmpty()) {
                    g.renderItem(all.get(slot), cx + 1, cy + 1);
                    g.renderItemDecorations(mc.font, all.get(slot), cx + 1, cy + 1);
                }
            }
        }
    }

    // ── Hit-test helpers ─────────────────────────────────────────────

    /** Returns the flat slot index (0–119) under the mouse, or -1. */
    public static int slotAt(double mouseX, double mouseY, int panelLeft, int panelTop) {
        int relX = (int)(mouseX - panelLeft);
        int relY = (int)(mouseY - gridTop(panelTop));
        if (relX < 0 || relX >= COLS * CELL) return -1;
        if (relY < 0 || relY >= TOTAL_ROWS * CELL) return -1;
        return (relY / CELL) * COLS + (relX / CELL);
    }

    /** True when mouse is over the "LOOT ALL" button. */
    public static boolean isLootAllButton(double mouseX, double mouseY,
                                          int panelLeft, int panelTop, int btnWidth) {
        int btnX = panelLeft + panelWidth() - btnWidth;
        int btnY = panelTop + 2;
        return mouseX >= btnX && mouseX <= panelLeft + panelWidth()
            && mouseY >= btnY && mouseY <= btnY + 9;
    }

    // Keep old separate helpers as thin wrappers so callers still compile
    public static int lootSlotAt(double mx, double my, int pl, int pt)     { return slotAt(mx, my, pl, pt); }
    public static int vicinitySlotAt(double mx, double my, int pl, int pt) { return slotAt(mx, my, pl, pt); }
}
