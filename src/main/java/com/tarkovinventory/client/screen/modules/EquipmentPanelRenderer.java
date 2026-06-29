package com.tarkovinventory.client.screen.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Visual renderer for the Tarkov equipment panel.
 *
 * This class is purely visual — it renders the equipment panel background,
 * silhouette, slot backgrounds, labels, and hover effects. All click/drag/swap
 * logic is handled by Forge's AbstractContainerScreen through real Slot objects
 * positioned at the coordinates this class provides.
 *
 * After calling init(), use getEquipForgePos() to position Forge Slots.
 */
public class EquipmentPanelRenderer {

    private final Minecraft mc = Minecraft.getInstance();

    private int left;
    private int top;

    private static final ResourceLocation SILHOUETTE =
            new ResourceLocation("tarkovinventory", "textures/ui/silhouette.png");
    private static final int SIL_W = 128;
    private static final int SIL_H = 256;

    /** Slot cell size for Forge slot alignment (standard Minecraft slot). */
    private static final int CELL = 18;
    private static final int GAP  = 2;
    private static final int STEP = CELL + GAP; // 20

    private final List<EquipmentSlot> visuals = new ArrayList<>();

    /** Panel dimensions. */
    public static final int PANEL_WIDTH  = 190;
    public static final int PANEL_HEIGHT = 260;

    // ── Init: compute layout ────────────────────────────────────────

    /**
     * Equipment layout with Forge slot index mapping.
     *
     * Forge menu slots for equipment (menu indices = CUSTOM_EQUIP_START + capSlot):
     *   slot 144 = HEAD (vanilla armor 0)
     *   slot 145 = CHEST (vanilla armor 1)
     *   slot 146 = LEGS (vanilla armor 2)
     *   slot 147 = FEET (vanilla armor 3)
     *   slot 148 = FACE (cap SLOT_FACE=0)
     *   slot 149 = EAR (cap SLOT_EARPIECE=1)
     *   slot 150 = RIG (cap SLOT_RIG=2)
     *   slot 151 = PANTS (cap SLOT_PANTS=3)
     *   slot 152 = KNEES (cap SLOT_KNEES=4)
     *   slot 153 = ARMBAND (cap SLOT_ARMBAND=5)
     *   slot 154 = BACKPACK (cap SLOT_ON_BACK=6)
     *
     * Hotbar slots (menu indices 182-190):
     *   182 = PRIMARY WEAPON (hotbar 0)
     *   183 = SECONDARY WEAPON (hotbar 1)
     */
    public void init(int left, int top) {
        this.left = left;
        this.top = top;

        visuals.clear();

        int x = left;
        int y = top;

        // Column positions (centered in panel)
        int col1X = x + 10;
        int col2X = x + (PANEL_WIDTH / 2) - (CELL / 2);
        int col3X = x + PANEL_WIDTH - 10 - CELL;

        int row = 0;
        int vStep = STEP; // 20px per row

        // Row 0: BALACLAVA/FACE (col1) + HEAD (col3)
        addVisual("FACE",   EquipmentSlotType.FACE, col1X, y + row * vStep, CELL, CELL);
        addVisual("HEAD",   EquipmentSlotType.HEAD, col3X, y + row * vStep, CELL, CELL);
        row++;

        // Row 1: EAR (col1) + CHEST (col3)
        addVisual("EAR",    EquipmentSlotType.EAR,  col1X, y + row * vStep, CELL, CELL);
        addVisual("CHEST",  EquipmentSlotType.ARMOR, col3X, y + row * vStep, CELL, CELL);
        row++;

        // Row 2: RIG (col1) + LEGS/PANTS (col3)
        addVisual("RIG",    EquipmentSlotType.RIG,  col1X, y + row * vStep, CELL, CELL);
        addVisual("PANTS",  EquipmentSlotType.PANTS, col3X, y + row * vStep, CELL, CELL);
        row++;

        // Row 3: KNEES (col2)
        addVisual("KNEES",  EquipmentSlotType.KNEE, col2X, y + row * vStep, CELL, CELL);
        row++;

        // Row 4: BOOTS (col2)
        addVisual("BOOTS",  EquipmentSlotType.BOOTS, col2X, y + row * vStep, CELL, CELL);
        row++;

        // Row 5: BACKPACK (col1)
        addVisual("BACKPACK", EquipmentSlotType.BACKPACK, col1X, y + row * vStep, CELL, CELL);
        row += 2;

        // Weapon slots — square slots below the silhouette (label to the right).
        // weaponY is fixed (y+180) to sit just under the WEAPONS divider (y+169).
        int weaponY = y + 180;
        int weaponX = x + 10;
        addVisual("PRIMARY",   EquipmentSlotType.WEAPON, weaponX, weaponY, CELL, CELL);
        addVisual("SECONDARY", EquipmentSlotType.WEAPON, weaponX, weaponY + CELL + GAP, CELL, CELL);
    }

    private void addVisual(String id, EquipmentSlotType type, int x, int y, int w, int h) {
        visuals.add(new EquipmentSlot(id, type, x, y, w, h));
    }

    // ── Hover ───────────────────────────────────────────────────────

    public void updateHover(double mouseX, double mouseY) {
        for (EquipmentSlot v : visuals) {
            v.hovered = v.isMouseOver(mouseX, mouseY);
        }
    }

    public EquipmentSlot getVisualSlot(String id) {
        for (EquipmentSlot v : visuals) {
            if (v.id.equals(id)) return v;
        }
        return null;
    }

    // ── Accessors for screen to position Forge slots ────────────────

    /**
     * Get the Forge slot position for a custom equipment capability slot index (0-6).
     * Returns int[]{forgeX, forgeY}.
     *
     * Mapping:
     *   0 (FACE)    → "FACE" visual
     *   1 (EAR)     → "EAR" visual
     *   2 (RIG)     → "RIG" visual
     *   3 (PANTS)   → "PANTS" visual
     *   4 (KNEES)   → "KNEES" visual
     *   5 (ARMBAND) → no visual (hidden)
     *   6 (BACKPACK)→ "BACKPACK" visual
     */
    public int[] getEquipForgePos(int capSlotIndex) {
        String id = switch (capSlotIndex) {
            case 0 -> "FACE";
            case 1 -> "EAR";
            case 2 -> "RIG";
            case 3 -> "PANTS";
            case 4 -> "KNEES";
            case 5 -> null; // armband has no visual
            case 6 -> "BACKPACK";
            default -> null;
        };
        if (id == null) return new int[]{-2000, -2000};

        EquipmentSlot vis = getVisualSlot(id);
        if (vis == null) return new int[]{-2000, -2000};
        return new int[]{vis.x1, vis.y1};
    }

    /**
     * Get the Forge slot position for vanilla armor slots.
     * armorIndex: 0=HEAD, 1=CHEST, 2=LEGS, 3=FEET
     */
    public int[] getArmorForgePos(int armorIndex) {
        String id = switch (armorIndex) {
            case 0 -> "HEAD";
            case 1 -> "CHEST";
            case 2 -> "PANTS";
            case 3 -> "BOOTS";
            default -> null;
        };
        if (id == null) return new int[]{-2000, -2000};

        EquipmentSlot vis = getVisualSlot(id);
        if (vis == null) return new int[]{-2000, -2000};
        return new int[]{vis.x1, vis.y1};
    }

    public int getLeft() { return left; }
    public int getTop() { return top; }

    // ── Render ──────────────────────────────────────────────────────

    public void render(GuiGraphics g) {
        render(g, false);
    }

    public void render(GuiGraphics g, boolean renderSilhouette) {
        int x = left;
        int y = top;

        // Panel background layers
        g.fill(x - 10, y - 10, x + PANEL_WIDTH + 10, y + PANEL_HEIGHT + 10, 0x66000000);
        g.fill(x - 6,  y - 6,  x + PANEL_WIDTH + 6,  y + PANEL_HEIGHT + 6,  0xFF101010);
        g.fill(x - 4,  y - 4,  x + PANEL_WIDTH + 4,  y + PANEL_HEIGHT + 4,  0xFF1A1A1A);

        // Panel title
        g.drawString(mc.font, "EQUIPMENT", x, y - 10, 0xFF888888, false);

        // Silhouette
        if (renderSilhouette) {
            int silX = x + (PANEL_WIDTH / 2) - (SIL_W / 2);
            int silY = y + 2;  // moved up so it doesn't crowd the weapons section
            g.blit(SILHOUETTE, silX, silY, 0, 0, SIL_W, SIL_H, SIL_W, SIL_H);
        }

        // Separator line + WEAPONS label — placed BELOW the silhouette so they
        // no longer overlap the character model.
        int weaponSepY = y + 169;
        g.fill(x + 4, weaponSepY, x + PANEL_WIDTH - 4, weaponSepY + 1, 0xFF2A2A2A);
        g.drawString(mc.font, "WEAPONS", x + PANEL_WIDTH / 2 - mc.font.width("WEAPONS") / 2,
                weaponSepY - 9, 0xFF666666, false);

        // Slot backgrounds (drawn under silhouette text)
        for (EquipmentSlot v : visuals) {
            renderSlotBackground(g, v);
        }
        // Labels drawn after items so they appear on top of slot backgrounds
        for (EquipmentSlot v : visuals) {
            renderSlotLabel(g, v);
        }
    }

    private void renderSlotBackground(GuiGraphics g, EquipmentSlot v) {
        int bg = v.hovered ? 0xFF242424 : 0xFF161616;

        g.fill(v.x1 - 1, v.y1 - 1, v.x2 + 1, v.y2 + 1, 0xFF000000);
        g.fill(v.x1, v.y1, v.x2, v.y2, bg);
        g.fill(v.x1, v.y1, v.x1 + 1, v.y2, 0xFF2A2A2A);
        g.fill(v.x1, v.y1, v.x2, v.y1 + 1, 0xFF3A3A3A);
        g.fill(v.x1 + 1, v.y1 + 1, v.x2 - 1, v.y2 - 1, 0xFF101010);
    }

    /**
     * Renders the slot label beside the slot (not above it) so it never
     * overlaps with the slot background or items in the row above.
     *
     *  col1 slots (x ≈ left+10)  → label to the RIGHT of the slot
     *  col3 slots (x ≈ left+162) → label to the LEFT  of the slot
     *  col2 slots (center)        → label to the RIGHT of the slot
     *  WEAPON slots (wide)        → label inside the slot, left-aligned
     */
    private void renderSlotLabel(GuiGraphics g, EquipmentSlot v) {
        String label = v.id;
        int fontH  = mc.font.lineHeight;           // 9 px
        int centY  = v.y1 + (CELL - fontH) / 2;   // vertically centred with slot

        if (v.type == EquipmentSlotType.WEAPON) {
            // Square slot — label to the right, like the other left-column slots.
            g.drawString(mc.font, label, v.x2 + 4, centY, 0xFF909090, false);
        } else if (v.x1 >= left + PANEL_WIDTH - 50) {
            // Right column (col3: HEAD, CHEST, PANTS, BOOTS) → label to the left
            int tw = mc.font.width(label);
            g.drawString(mc.font, label, v.x1 - tw - 4, centY, 0xFF909090, false);
        } else {
            // Left column (col1) and centre column (col2) → label to the right
            g.drawString(mc.font, label, v.x2 + 4, centY, 0xFF909090, false);
        }
    }
}
