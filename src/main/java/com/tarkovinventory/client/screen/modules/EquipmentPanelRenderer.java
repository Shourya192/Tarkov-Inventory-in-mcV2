package com.tarkovinventory.client.screen.modules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;

import java.util.*;

/**
 * Visual renderer for the Tarkov equipment panel.
 *
 * This class is purely visual — it renders the equipment panel background,
 * silhouette, slot backgrounds, labels, and hover effects. All click/drag/swap
 * logic is handled by Forge's AbstractContainerScreen through real Slot objects
 * positioned at the coordinates this class provides.
 *
 * After calling init(), use getSlotLayout() to position Forge Slots.
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

    private final List<SlotLayout> layouts = new ArrayList<>();
    private final Map<String, SlotLayout> layoutMap = new LinkedHashMap<>();

    // Visual-only slots for rendering (hover, backgrounds, labels)
    private final List<EquipmentSlot> visuals = new ArrayList<>();

    /** Panel dimensions. */
    public static final int PANEL_WIDTH  = 190;
    public static final int PANEL_HEIGHT = 260;

    // ── Layout entry: maps a visual slot to Forge slot position ─────

    public static class SlotLayout {
        public final String id;
        public final EquipmentSlotType type;
        public final int forgeX;   // Forge slot x position (for Slot object)
        public final int forgeY;   // Forge slot y position (for Slot object)
        public final int visualX;  // visual top-left x
        public final int visualY;  // visual top-left y
        public final int visualW;  // visual width
        public final int visualH;  // visual height
        public final int menuSlotIndex; // index in menu.slots (-1 for hotbar-only)

        public SlotLayout(String id, EquipmentSlotType type, int forgeX, int forgeY,
                          int visualX, int visualY, int visualW, int visualH, int menuSlotIndex) {
            this.id = id;
            this.type = type;
            this.forgeX = forgeX;
            this.forgeY = forgeY;
            this.visualX = visualX;
            this.visualY = visualY;
            this.visualW = visualW;
            this.visualH = visualH;
            this.menuSlotIndex = menuSlotIndex;
        }

        public boolean isMouseOver(double mx, double my) {
            return mx >= visualX && mx <= visualX + visualW
                    && my >= visualY && my <= visualY + visualH;
        }
    }

    // ── Init: compute layout ────────────────────────────────────────

    /**
     * Equipment capability slot IDs in menu order:
     *   index 0 (EQUIP_START+0) = EARPIECE → maps to "EAR"
     *   index 1 (EQUIP_START+1) = ARMBAND  → maps to "BALACLAVA"
     *   index 2 (EQUIP_START+2) = ON_BACK  → maps to "BACKPACK"
     *
     * Hotbar slots:
     *   HOTBAR_START+0 = PRIMARY WEAPON
     *   HOTBAR_START+1 = SECONDARY WEAPON
     */
    public void init(int left, int top) {
        this.left = left;
        this.top = top;

        layouts.clear();
        layoutMap.clear();
        visuals.clear();

        int x = left;
        int y = top;

        // Column positions (centered in panel)
        int col1X = x + 10;
        int col2X = x + (PANEL_WIDTH / 2) - (CELL / 2);
        int col3X = x + PANEL_WIDTH - 10 - CELL;

        int row = 0;
        int vStep = STEP; // 20px per row

        // Row 0: BALACLAVA (col1) + HEAD (col3)
        addLayout("BALACLAVA", EquipmentSlotType.FACE, col1X, y + row * vStep, CELL, CELL, 1);
        addLayout("HEAD",       EquipmentSlotType.HEAD, col3X, y + row * vStep, CELL, CELL, -1);
        row++;

        // Row 1: EAR (col1) + FACE (col3)
        addLayout("EAR",  EquipmentSlotType.EAR, col1X, y + row * vStep, CELL, CELL, 0);
        addLayout("FACE", EquipmentSlotType.FACE, col3X, y + row * vStep, CELL, CELL, -1);
        row++;

        // Row 2: RIG (col1) + CHEST (col3)
        addLayout("RIG",   EquipmentSlotType.RIG,   col1X, y + row * vStep, CELL, CELL, -1);
        addLayout("CHEST", EquipmentSlotType.ARMOR,  col3X, y + row * vStep, CELL, CELL, -1);
        row++;

        // Row 3: PANTS (col2)
        addLayout("PANTS", EquipmentSlotType.PANTS, col2X, y + row * vStep, CELL, CELL, -1);
        row++;

        // Row 4: KNEES (col2)
        addLayout("KNEES", EquipmentSlotType.KNEE, col2X, y + row * vStep, CELL, CELL, -1);
        row++;

        // Row 5: BOOTS (col2)
        addLayout("BOOTS", EquipmentSlotType.BOOTS, col2X, y + row * vStep, CELL, CELL, -1);
        row++;

        // Row 6: BACKPACK (col1)
        addLayout("BACKPACK", EquipmentSlotType.BACKPACK, col1X, y + row * vStep, CELL, CELL, 2);
        row += 2;

        // Weapon slots (full-width)
        int weaponY = y + row * vStep;
        int weaponW = PANEL_WIDTH - 20;
        int weaponX = x + 10;
        addLayout("PRIMARY",   EquipmentSlotType.WEAPON, weaponX, weaponY, weaponW, CELL, -1);
        addLayout("SECONDARY", EquipmentSlotType.WEAPON, weaponX, weaponY + CELL + GAP, weaponW, CELL, -1);
    }

    private void addLayout(String id, EquipmentSlotType type, int fx, int fy, int vw, int vh, int capIdx) {
        // Forge slot position = center of visual slot
        int forgeX = fx;
        int forgeY = fy;

        SlotLayout layout = new SlotLayout(id, type, forgeX, forgeY, fx, fy, vw, vh, capIdx);
        layouts.add(layout);
        layoutMap.put(id, layout);

        // Create matching visual EquipmentSlot for rendering
        visuals.add(new EquipmentSlot(id, type, fx, fy, vw, vh));
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

    public SlotLayout getLayout(String id) {
        return layoutMap.get(id);
    }

    public List<SlotLayout> getLayouts() {
        return Collections.unmodifiableList(layouts);
    }

    /** Get the Forge slot position for equipment capability slot index (0-2). */
    public int[] getEquipForgePos(int capSlotIndex) {
        for (SlotLayout l : layouts) {
            if (l.menuSlotIndex == capSlotIndex) return new int[]{l.forgeX, l.forgeY};
        }
        return new int[]{-1000, -1000};
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

        // Silhouette
        if (renderSilhouette) {
            int silX = x + (PANEL_WIDTH / 2) - (SIL_W / 2);
            int silY = y + 10;
            g.blit(SILHOUETTE, silX, silY, 0, 0, SIL_W, SIL_H, SIL_W, SIL_H);
        }

        // Draw slot backgrounds and labels (items are drawn by Forge slot rendering)
        for (EquipmentSlot v : visuals) {
            renderSlotBackground(g, v);
        }
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

    private void renderSlotLabel(GuiGraphics g, EquipmentSlot v) {
        g.drawString(mc.font, v.id, v.x1, v.y1 - 10, 0xFFB0B0B0, false);
    }
}
