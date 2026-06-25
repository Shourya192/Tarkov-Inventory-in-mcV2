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

        // Rig background box
        int rigW = menu.getRigCols() * TarkovInventoryMenu.CELL;
        int rigH = menu.getRigRows() * TarkovInventoryMenu.CELL;
        g.fill(centerScreenX - 4, topPos + menu.getRigGridY() - 4,
               centerScreenX + rigW + 4, topPos + menu.getRigGridY() + rigH + 4, 0xFF0D0D0D);

        // Loot panel — only renders when activeLootCount > 0 or isCorpse
        rightPanel.render(g, rightScreenX, screenTopY,
                menu.getActiveLootCount(), menu.getActiveLootOwner(),
                menu.activeLootIsCorpse,
                menu.hasActiveLootSource(),
                menu.corpseRigCols,  menu.corpseRigRows,
                menu.corpseGridCols, menu.corpseGridRows);

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

    /**
     * Forge's getSlotUnderMouse() checks slot positions but not isActive() in this build.
     * We clear hoveredSlot after super.render() if the hovered slot is inactive.
     */
    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        grid.updateHover(mouseX, mouseY);
        equipment.updateHover(mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        // Prevent inactive slots (e.g. rig/backpack slots when nothing equipped)
        // from being treated as hovered — clear hoveredSlot if it is not active.
        if (hoveredSlot != null && !hoveredSlot.isActive()) {
            hoveredSlot = null;
        }
        renderTooltip(g, mouseX, mouseY);
    }

    @Override public boolean isPauseScreen() { return false; }
}
