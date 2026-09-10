package com.tarkovinventory.client;

import com.tarkovinventory.client.screen.AdminTagScreen;
import net.minecraft.client.Minecraft;

/**
 * Client-only helpers invoked from S2C packet handlers via DistExecutor, so the
 * client-only classes (Screen, Minecraft) are never loaded on a dedicated server.
 */
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {}

    public static void openAdminTagScreen() {
        Minecraft.getInstance().setScreen(new AdminTagScreen());
    }
}
