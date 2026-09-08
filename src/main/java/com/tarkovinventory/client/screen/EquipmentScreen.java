package com.tarkovinventory.client.screen;

import com.tarkovinventory.client.screen.modules.EquipmentPanelRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Standalone equipment panel screen (used for debug/testing).
 * Input is not handled here — the full TarkovInventoryScreen handles all
 * equipment interactions through Forge's slot system.
 */
public class EquipmentScreen extends Screen {

    private final EquipmentPanelRenderer panel = new EquipmentPanelRenderer();

    public EquipmentScreen() {
        super(Component.literal("Equipment"));
    }

    @Override
    protected void init() {
        super.init();
        int left = this.width / 2 - 120;
        int top = this.height / 2 - 120;
        panel.init(left, top);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics);
        panel.updateHover(mouseX, mouseY);
        panel.render(graphics, true);
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
