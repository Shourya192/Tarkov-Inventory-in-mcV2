package com.tarkovinventory.client.screen.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Renders the right-hand "loot / vicinity" panel.
 *
 * Top section  (LOOT)     — items from a nearby corpse or opened container,
 *                           populated by CorpseClientCache via S2CCorpseContentsPacket.
 * Bottom section (VICINITY) — ItemEntity stacks visible within ~6 blocks,
 *                           read directly from the client Level each frame.
 *
 * Hit-testing helpers (lootSlotAt / vicinitySlotAt) are used by
 * TarkovInventoryScreen.mouseClicked to dispatch the correct network packet.
 */
public class RightInventoryPanelRenderer {

    // ── Layout constants (public so TarkovInventoryScreen can share them) ──
    public static final int COLS        = 10;
    public static final int LOOT_ROWS   = 8;
    public static final int VIC_ROWS    = 4;
    public static final int CELL        = 18;
    public static final int HEADER_H    = 14;   // space above each grid for its label
    public static final int DIVIDER_H   = 14;   // gap between loot grid and vicinity grid

    public static int panelWidth()  { return COLS * CELL; }
    public static int panelHeight() {
        return HEADER_H + LOOT_ROWS * CELL + DIVIDER_H + VIC_ROWS * CELL + 8;
    }

    // Absolute Y of the first loot cell
    public static int lootGridTop(int panelTop)     { return panelTop + HEADER_H; }
    // Absolute Y of the first vicinity cell
    public static int vicinityGridTop(int panelTop) {
        return lootGridTop(panelTop) + LOOT_ROWS * CELL + DIVIDER_H;
    }

    private final Minecraft mc = Minecraft.getInstance();

    /**
     * Full render call — draws background, section headers, grid cells, and items.
     *
     * @param lootItems      flat item list from active corpse/container (may be empty)
     * @param lootOwner      display name to show in the loot header, or "" if nothing open
     * @param vicinityStacks item-entity stacks visible on the ground nearby
     * @param hoveredSlot    slot index under the mouse:
     *                         0–79  → loot slot
     *                         80–119 → vicinity slot (offset 80)
     *                         -1    → nothing
     */
    public void render(GuiGraphics g, int left, int top,
                       List<ItemStack> lootItems,  String lootOwner,
                       List<ItemStack> vicinityStacks, int hoveredSlot) {

        int width       = panelWidth();
        int totalHeight = panelHeight();
        int lootGridY   = lootGridTop(top);
        int vicGridY    = vicinityGridTop(top);

        // ── Background ────────────────────────────────────────────
        g.fill(left - 8, top - 8, left + width + 8, top + totalHeight + 4, 0xFF0B0B0B);
        g.fill(left - 4, top - 4, left + width + 4, top + totalHeight,     0xFF151515);

        // ── LOOT section header ───────────────────────────────────
        String lootLabel  = lootOwner.isEmpty() ? "LOOT" : lootOwner + "'s loot";
        String lootAllBtn = lootItems.isEmpty() ? "" : "[LOOT ALL]";
        g.drawString(mc.font, lootLabel,  left, top + 2, 0xFFCCCC88, false);
        if (!lootAllBtn.isEmpty()) {
            int btnX = left + width - mc.font.width(lootAllBtn);
            g.drawString(mc.font, lootAllBtn, btnX, top + 2, 0xFF888855, false);
        }

        // ── Loot grid ─────────────────────────────────────────────
        for (int row = 0; row < LOOT_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int slot = row * COLS + col;
                int cx   = left + col * CELL;
                int cy   = lootGridY + row * CELL;
                boolean hov = hoveredSlot == slot;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, hov ? 0xFF2E2E18 : 0xFF1E1E12);
                if (slot < lootItems.size() && !lootItems.get(slot).isEmpty()) {
                    g.renderItem(lootItems.get(slot), cx + 1, cy + 1);
                    g.renderItemDecorations(mc.font, lootItems.get(slot), cx + 1, cy + 1);
                }
            }
        }

        // ── Vicinity section header + divider ─────────────────────
        int divY = lootGridY + LOOT_ROWS * CELL;
        g.fill(left, divY + 3, left + width, divY + 4, 0xFF333322);
        g.drawString(mc.font, "VICINITY", left, divY + 5, 0xFFCCCC88, false);

        // ── Vicinity grid ─────────────────────────────────────────
        for (int row = 0; row < VIC_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int vicIdx = row * COLS + col;
                int slot   = 80 + vicIdx;          // offset so loot and vicinity don't share indices
                int cx     = left + col * CELL;
                int cy     = vicGridY + row * CELL;
                boolean hov = hoveredSlot == slot;
                g.fill(cx, cy, cx + CELL - 1, cy + CELL - 1, hov ? 0xFF182028 : 0xFF101820);
                if (vicIdx < vicinityStacks.size() && !vicinityStacks.get(vicIdx).isEmpty()) {
                    g.renderItem(vicinityStacks.get(vicIdx), cx + 1, cy + 1);
                    g.renderItemDecorations(mc.font, vicinityStacks.get(vicIdx), cx + 1, cy + 1);
                }
            }
        }
    }

    // ── Hit-test helpers ─────────────────────────────────────────────

    /**
     * Returns the loot slot index (0–79) at the given screen position,
     * or -1 if the mouse is not over the loot grid.
     */
    public static int lootSlotAt(double mouseX, double mouseY, int panelLeft, int panelTop) {
        return gridSlotAt(mouseX, mouseY, panelLeft, lootGridTop(panelTop), LOOT_ROWS);
    }

    /**
     * Returns the vicinity slot index (0–39) at the given screen position,
     * or -1 if the mouse is not over the vicinity grid.
     */
    public static int vicinitySlotAt(double mouseX, double mouseY, int panelLeft, int panelTop) {
        return gridSlotAt(mouseX, mouseY, panelLeft, vicinityGridTop(panelTop), VIC_ROWS);
    }

    /** True if the mouse is over the "LOOT ALL" button text. */
    public static boolean isLootAllButton(double mouseX, double mouseY,
                                          int panelLeft, int panelTop,
                                          int lootAllTextWidth) {
        int btnX = panelLeft + panelWidth() - lootAllTextWidth;
        int btnY = panelTop + 2;
        return mouseX >= btnX && mouseX <= panelLeft + panelWidth()
            && mouseY >= btnY && mouseY <= btnY + 9;
    }

    private static int gridSlotAt(double mx, double my,
                                   int gridLeft, int gridTop, int rows) {
        int relX = (int)(mx - gridLeft);
        int relY = (int)(my - gridTop);
        if (relX < 0 || relX >= COLS * CELL) return -1;
        if (relY < 0 || relY >= rows * CELL)  return -1;
        return (relY / CELL) * COLS + (relX / CELL);
    }
}
