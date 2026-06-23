package com.tarkovinventory.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.tarkovinventory.client.screen.modules.EquipmentPanelRenderer;
import com.tarkovinventory.client.screen.modules.EquipmentSlot;
import com.tarkovinventory.client.screen.modules.InventoryGridRenderer;
import com.tarkovinventory.client.screen.modules.RightInventoryPanelRenderer;
import com.tarkovinventory.container.TarkovInventoryMenu;
import com.tarkovinventory.inventory.GridInventory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

/**
 * Tarkov inventory UI backed by the Forge menu system.
 *
 * All interactions go through Forge's AbstractContainerScreen slot engine:
 * - Equipment slots: real Forge Slots backed by EquipmentContainer (vanilla armor + custom)
 * - Grid slots: real Forge Slots backed by GridInventory
 * - Rig slots: real Forge Slots backed by RigContainer
 * - Player inventory/hotbar: standard Forge Slots
 *
 * Mouse click, drag, pickup, drop, and swap are all handled by the parent class.
 * This class focuses on layout, rendering, and rig/backpack sync.
 */
public class TarkovInventoryScreen extends AbstractContainerScreen<TarkovInventoryMenu> {

    private final EquipmentPanelRenderer equipment = new EquipmentPanelRenderer();
    private final InventoryGridRenderer grid = new InventoryGridRenderer();
    private final RightInventoryPanelRenderer rightPanel = new RightInventoryPanelRenderer();

    private int leftX, centerX, rightX, topY;

    // Client-side rig/backpack dimensions (populated by sync packets)
    private int clientRigCols = 3, clientRigRows = 3;
    private int clientBpCols = 3, clientBpRows = 3;

    public TarkovInventoryScreen(TarkovInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 700;
        this.imageHeight = 400;
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();

        // ── Compute panel positions ─────────────────────────────
        int spacing = 20;
        int equipW = EquipmentPanelRenderer.PANEL_WIDTH;
        int rigCols = menu.getRigContainer().getCols();
        int rigRows = menu.getRigContainer().getRows();
        int bpCols = menu.getGridInventory().getActiveCols();
        int bpRows = menu.getGridInventory().getActiveRows();
        int gridW = Math.max(bpCols, rigCols) * 18 + 32;
        int rightW = (10 * 18) + 16;
        int totalW = equipW + gridW + rightW + (spacing * 2);
        int startX = (this.width - totalW) / 2;

        this.topY = Math.max(18, (this.height - 380) / 2);
        this.leftX = startX;
        this.centerX = leftX + equipW + spacing;
        this.rightX = centerX + gridW + spacing;

        // ── Init renderers ──────────────────────────────────────
        equipment.init(leftX, topY);
        grid.init(centerX, topY, bpCols, bpRows, rigCols, rigRows);

        // ── Position all Forge menu slots ───────────────────────
        layoutAllSlots();
    }

    private void layoutAllSlots() {
        // ── 1. Grid slots (backpack grid) ───────────────────────
        int bpBaseY = grid.getBackpackY();
        int activeCols = menu.getGridInventory().getActiveCols();
        int activeRows = menu.getGridInventory().getActiveRows();
        for (int i = 0; i < GridInventory.MAX_CELLS; i++) {
            Slot slot = menu.slots.get(i);
            int col = i % GridInventory.MAX_COLS;
            int row = i / GridInventory.MAX_COLS;
            if (col < activeCols && row < activeRows) {
                slot.x = centerX + col * 18;
                slot.y = bpBaseY + row * 18;
            } else {
                slot.x = -2000;
                slot.y = -2000;
            }
        }

        // ── 2. Vanilla armor slots (head, chest, legs, feet) ────
        for (int armorIdx = 0; armorIdx < 4; armorIdx++) {
            int menuIdx = TarkovInventoryMenu.VANILLA_ARMOR_START + armorIdx;
            int[] pos = equipment.getArmorForgePos(armorIdx);
            Slot slot = menu.slots.get(menuIdx);
            slot.x = pos[0];
            slot.y = pos[1];
        }

        // ── 3. Custom equipment slots (face, ear, rig, pants, knees, armband, backpack) ──
        for (int capIdx = 0; capIdx < TarkovInventoryMenu.CUSTOM_EQUIP_COUNT; capIdx++) {
            int menuIdx = TarkovInventoryMenu.CUSTOM_EQUIP_START + capIdx;
            int[] pos = equipment.getEquipForgePos(capIdx);
            Slot slot = menu.slots.get(menuIdx);
            slot.x = pos[0];
            slot.y = pos[1];
        }

        // ── 4. Player main inventory (right panel) ──────────────
        int mainY = topY + 20;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                Slot slot = menu.slots.get(TarkovInventoryMenu.PLAYER_START + row * 9 + col);
                slot.x = rightX + col * 18;
                slot.y = mainY + row * 18;
            }
        }

        // ── 5. Hotbar slots ─────────────────────────────────────
        // Hotbar 0 → PRIMARY weapon slot (in equipment panel)
        EquipmentSlot primary = equipment.getVisualSlot("PRIMARY");
        EquipmentSlot secondary = equipment.getVisualSlot("SECONDARY");
        menu.slots.get(TarkovInventoryMenu.HOTBAR_START).x = primary != null ? primary.x1 : centerX;
        menu.slots.get(TarkovInventoryMenu.HOTBAR_START).y = primary != null ? primary.y1 : topY + 200;
        menu.slots.get(TarkovInventoryMenu.HOTBAR_START + 1).x = secondary != null ? secondary.x1 : centerX;
        menu.slots.get(TarkovInventoryMenu.HOTBAR_START + 1).y = secondary != null ? secondary.y1 : topY + 220;

        // Hotbar 2-8 → pocket slots (in center panel pockets row)
        for (int i = 0; i < TarkovInventoryMenu.POCKETS_COUNT; i++) {
            Slot slot = menu.slots.get(TarkovInventoryMenu.HOTBAR_START + 2 + i);
            slot.x = grid.getPocketX(i);
            slot.y = grid.getPocketY();
        }

        // ── 6. Rig panel slots ──────────────────────────────────
        int rigGridY = grid.getRigGridY();
        int rigCols = grid.getRigCols();
        int rigStart = menu.getRigStartIndex();
        int rigCount = menu.getRigSlotCount();
        for (int i = 0; i < rigCount; i++) {
            int col = i % rigCols;
            int row = i / rigCols;
            Slot slot = menu.slots.get(rigStart + i);
            slot.x = centerX + col * 18;
            slot.y = rigGridY + row * 18;
        }

        // ── 7. Backpack panel slots ─────────────────────────────
        int bpPanelStart = menu.getBackpackPanelStartIndex();
        int bpPanelCount = menu.getBackpackPanelSlotCount();
        int bpPanelY = grid.getBackpackY() + (activeRows * 18) + 30;
        int bpPanelCols = Math.max(1, clientBpCols);
        for (int i = 0; i < bpPanelCount; i++) {
            int col = i % bpPanelCols;
            int row = i / bpPanelCols;
            Slot slot = menu.slots.get(bpPanelStart + i);
            slot.x = centerX + col * 18;
            slot.y = bpPanelY + row * 18;
        }
    }

    // ── Render ──────────────────────────────────────────────────────

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        grid.updateHover(mouseX, mouseY);
        equipment.updateHover(mouseX, mouseY);
        // super.render handles all slot rendering including rig slots via Forge sync
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.disableDepthTest();
        equipment.render(g, true);
        grid.renderWithBackground(g);
        rightPanel.render(g, rightX, topY);
        renderRigPanelBackground(g);
        RenderSystem.enableDepthTest();
    }

    private void renderRigPanelBackground(GuiGraphics g) {
        int rigGridY = grid.getRigGridY();
        int rigCols = grid.getRigCols();
        int rigRows = grid.getRigRows();
        int w = rigCols * 18;
        int h = rigRows * 18;
        g.fill(centerX - 4, rigGridY - 4, centerX + w + 4, rigGridY + h + 4, 0xFF0D0D0D);
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {
        g.fill(0, 0, width, height, 0xCC000000);
    }

    // ── Sync from server (called by S2CRigSyncPacket) ───────────────

    public void applyRigSync(int mode, CompoundTag data, int cols, int rows) {
        if (mode == 0) {
            clientRigCols = cols;
            clientRigRows = rows;
            grid.setRigDimensions(cols, rows);
        } else {
            clientBpCols = cols;
            clientBpRows = rows;
            grid.setBackpackDimensions(cols, rows);
        }

        // Re-layout slots to match new dimensions
        if (minecraft != null && minecraft.player != null) {
            layoutAllSlots();
        }
    }

    // ── Standard screen behavior ────────────────────────────────────

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
