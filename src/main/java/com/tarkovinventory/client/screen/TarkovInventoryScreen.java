package com.tarkovinventory.client.screen;

import com.tarkovinventory.inventory.RigContainer;
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
import org.jetbrains.annotations.NotNull;

/**
 * Tarkov inventory UI backed by the Forge menu system.
 *
 * All slot positions are set in the menu constructor (Slot.x/y are final).
 * This screen only renders backgrounds, handles hover, and draws tooltips.
 * Click, drag, pickup, drop, and swap are all handled by AbstractContainerScreen.
 */
public class TarkovInventoryScreen extends AbstractContainerScreen<TarkovInventoryMenu> {

    private final EquipmentPanelRenderer equipment = new EquipmentPanelRenderer();
    private final InventoryGridRenderer grid = new InventoryGridRenderer();
    private final RightInventoryPanelRenderer rightPanel = new RightInventoryPanelRenderer();

    // Screen-space positions (set in init())
    private int equipScreenX, centerScreenX, rightScreenX, screenTopY;

    public TarkovInventoryScreen(TarkovInventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // imageWidth/Height come from menu layout
        this.imageWidth = menu.getTotalImageWidth();
        this.imageHeight = menu.getTotalImageHeight();
        this.inventoryLabelY = -1000;
        this.titleLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();

        // Calculate screen-space panel positions
        // leftPos/topPos are set by AbstractContainerScreen based on imageWidth/Height
        this.equipScreenX = leftPos;
        this.centerScreenX = leftPos + menu.getCenterPanelX();
        this.rightScreenX = leftPos + menu.getRightPanelX();
        this.screenTopY = topPos;

        // Init renderers with screen-space positions
        equipment.init(equipScreenX, screenTopY);

        grid.init(
            centerScreenX,
            screenTopY,
            menu.getBpCols(),
            menu.getBpRows(),
            menu.getRigCols(),
            menu.getRigRows()
        );
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        grid.updateHover(mouseX, mouseY);
        equipment.updateHover(mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.disableDepthTest();

        // Equipment panel background
        equipment.render(g, true);

        // Center panel background (pockets + rig + backpack grid)
        grid.renderWithBackground(g);

        // Right panel background (player inventory)
        rightPanel.render(g, rightScreenX, screenTopY);

        // Rig panel background
        int rigW = menu.getRigCols() * TarkovInventoryMenu.CELL;
        int rigH = menu.getRigRows() * TarkovInventoryMenu.CELL;
        int rigY = topPos + menu.getRigGridY();
        g.fill(centerScreenX - 4, rigY - 4, centerScreenX + rigW + 4, rigY + rigH + 4, 0xFF0D0D0D);

        RenderSystem.enableDepthTest();
    }

    @Override
    public void renderBackground(@NotNull GuiGraphics g) {
        g.fill(0, 0, width, height, 0xCC000000);
    }

    /**
     * Called by S2CRigSyncPacket to update rig (mode=0) or backpack (mode=1)
     * inventory contents and resize the grid renderer accordingly.
     */
    public void applyRigSync(int mode, CompoundTag data, int cols, int rows) {
        RigContainer container = mode == 0 ? menu.getRigContainer() : menu.getBackpackContainer();
        if (container.getRigInventory() != null) {
            container.getRigInventory().deserializeNBT(data);
        }
        if (mode == 0) {
            grid.setRigDimensions(cols, rows);
        } else {
            grid.setBackpackDimensions(cols, rows);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
