package com.tarkovinventory.client.screen;

import com.tarkovinventory.inventory.RigContainer;
import com.tarkovinventory.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tarkovinventory.client.screen.modules.EquipmentPanelRenderer;
import com.tarkovinventory.client.screen.modules.InventoryGridRenderer;
import com.tarkovinventory.client.screen.modules.RightInventoryPanelRenderer;
import com.tarkovinventory.container.TarkovInventoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class TarkovInventoryScreen extends AbstractContainerScreen<TarkovInventoryMenu> {

    private final EquipmentPanelRenderer      equipment  = new EquipmentPanelRenderer();
    private final InventoryGridRenderer       grid       = new InventoryGridRenderer();
    private final RightInventoryPanelRenderer rightPanel = new RightInventoryPanelRenderer();

    private int equipScreenX, centerScreenX, rightScreenX, screenTopY;

    public TarkovInventoryScreen(TarkovInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth      = menu.getTotalImageWidth();
        this.imageHeight     = menu.getTotalImageHeight();
        this.inventoryLabelY = -1000;
        this.titleLabelY     = -1000;
    }

    @Override
    protected void init() {
        super.init();
        this.equipScreenX  = leftPos;
        this.centerScreenX = leftPos + menu.getCenterPanelX();
        this.rightScreenX  = leftPos + menu.getRightPanelX();
        this.screenTopY    = topPos;

        equipment.init(equipScreenX, screenTopY);
        grid.init(
            centerScreenX, screenTopY,
            menu.getBpCols(), menu.getBpRows(),
            menu.getRigCols(), menu.getRigRows(),
            menu.getRigContainer().isItemEquipped(),
            menu.getBackpackContainer().isItemEquipped()
        );
    }

    // ── Render ───────────────────────────────────────────────────────
    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.disableDepthTest();

        equipment.render(g, true);
        grid.renderWithBackground(g);

        // Loot panel — only renders when activeLootCount > 0 or isCorpse
        rightPanel.render(g, rightScreenX, screenTopY,
                menu.getActiveLootCount(), menu.getActiveLootOwner(),
                menu.activeLootIsCorpse,
                menu.hasActiveLootSource(),
                menu.corpseRigCols,  menu.corpseRigRows,
                menu.corpseGridCols, menu.corpseGridRows,
                menu.getSearchCurrentSlot(),
                menu.getSearchRequired(),
                !menu.getLootItem(TarkovInventoryMenu.CORPSE_GEAR_BASE).isEmpty(),
                !menu.getLootItem(TarkovInventoryMenu.CORPSE_GEAR_BASE + 1).isEmpty());

        RenderSystem.enableDepthTest();
    }


    @Override
    public void renderBackground(@NotNull GuiGraphics g) {
        g.fill(0, 0, width, height, 0xCC000000);
    }

    // ── Input ────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && menu.getActiveLootCount() > 0
                && RightInventoryPanelRenderer.isLootAllButton(
                        mouseX, mouseY, rightScreenX, screenTopY)) {
            // Shift-move every visible loot slot into player storage
            for (int i = 0; i < menu.getActiveLootCount(); i++) {
                int idx = menu.getLootSlotBase() + i;
                Slot s  = menu.slots.get(idx);
                if (!s.getItem().isEmpty()) {
                    slotClicked(s, idx, 0,
                            net.minecraft.world.inventory.ClickType.QUICK_MOVE);
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ── Rig sync (called by S2CRigSyncPacket) ────────────────────────

    public void applyRigSync(int mode, CompoundTag data, int cols, int rows) {
        RigContainer container = mode == 0 ? menu.getRigContainer() : menu.getBackpackContainer();
        if (container.getRigInventory() != null) container.getRigInventory().deserializeNBT(data);
        if (mode == 0) grid.setRigDimensions(cols, rows);
        else grid.setBackpackDimensions(cols, rows);
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        // Update live equipped state every frame so grid disappears instantly on unequip
        grid.setEquippedState(
                menu.getRigContainer().isItemEquipped(),
                menu.getBackpackContainer().isItemEquipped()
        );
        grid.updateHover(mouseX, mouseY);
        equipment.updateHover(mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        if (hoveredSlot != null && !hoveredSlot.isActive()) hoveredSlot = null;
        updateHoveredLootSlot(mouseX, mouseY);
        // Cover dark squares MC draws for inactive slots at visible positions.
        // getItem() now returns EMPTY for those slots so no items render on them,
        // but MC still draws the dark slot rectangle — paint over with UI background.
        coverAllInactiveSlots(g);
        drawSearchOverlay(g);
        renderTooltip(g, mouseX, mouseY);
    }

    /**
     * Paints over dark squares Minecraft renders for every inactive slot at a
     * visible screen position (rig grid when no rig, backpack grid when no backpack,
     * corpse content slots when gear item taken).
     */
    private void coverAllInactiveSlots(GuiGraphics g) {
        for (var slot : menu.slots) {
            // slot.x < -100 means deliberately hidden (off-screen at -2000)
            if (!slot.isActive() && slot.x > -100 && slot.y > -100) {
                g.fill(leftPos + slot.x - 1, topPos + slot.y - 1,
                       leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF0D0D0D);
            }
        }
    }

    private void updateHoveredLootSlot(int mouseX, int mouseY) {
        int base = menu.getLootSlotBase();
        if (base < 0) return;
        for (int i = 0; i < TarkovInventoryMenu.LOOT_SLOTS; i++) {
            var slot = menu.slots.get(base + i);
            if (!slot.isActive()) continue;
            int sx = leftPos + slot.x, sy = topPos + slot.y;
            if (mouseX >= sx && mouseX < sx + 16 && mouseY >= sy && mouseY < sy + 16) {
                menu.setHoveredLootSlot(i);
                return;
            }
        }
        menu.setHoveredLootSlot(-1);
    }

    /**
     * Draws the gold pulsing border on the slot currently being searched.
     * Items are hidden via getItem() override (returns EMPTY when unsearched),
     * so there is no z-ordering issue — the slot is visually empty and the
     * border is clearly visible. The renderer background colours already
     * distinguish searched vs unsearched cells.
     */
    private void drawSearchOverlay(GuiGraphics g) {
        if (!menu.hasActiveLootSource()) return;
        int base = menu.getLootSlotBase();
        if (base < 0) return;

        int currentSlot = menu.getSearchCurrentSlot();
        if (currentSlot < 0) return; // all searched or nothing active
        if (menu.isSlotSearchedClient(currentSlot)) return; // already done

        int progress = menu.getSearchProgress();
        int required = menu.getSearchRequired();
        boolean pulse = (System.currentTimeMillis() / 400) % 2 == 0;

        var slot = menu.slots.get(base + currentSlot);
        if (!slot.isActive()) return;
        int sx = leftPos + slot.x;
        int sy = topPos + slot.y;

        // Progress fill from bottom (gold tint grows upward as search progresses)
        if (required > 0 && progress > 0) {
            float pct = Math.min(1f, (float) progress / required);
            int fillH = (int) (16 * pct);
            g.fill(sx, sy + 16 - fillH, sx + 16, sy + 16, 0x88DDBB00);
        }
        // Pulsing gold border
        int bc = pulse ? 0xFFFFDD33 : 0xFFCCAA00;
        g.fill(sx - 1, sy - 1, sx + 17, sy,       bc);
        g.fill(sx - 1, sy + 16, sx + 17, sy + 17,  bc);
        g.fill(sx - 1, sy,      sx,      sy + 16,  bc);
        g.fill(sx + 16, sy,     sx + 17, sy + 16,  bc);
    }

    @Override public boolean isPauseScreen() { return false; }
}
