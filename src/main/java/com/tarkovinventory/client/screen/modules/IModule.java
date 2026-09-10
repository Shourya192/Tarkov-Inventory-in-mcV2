package com.tarkovinventory.client.screen.modules;

import com.tarkovinventory.client.screen.DragState;
import net.minecraft.client.gui.GuiGraphics;

public interface IModule {

    default void render(GuiGraphics g, int left, int top, int mouseX, int mouseY) {}

    default void mouseClicked(double mouseX, double mouseY, int button, int left, int top) {}

    default void mouseReleased(double mouseX, double mouseY, int button) {}

    default void mouseDragged(double mouseX, double mouseY, int button, double dx, double dy, DragState drag) {}
}
