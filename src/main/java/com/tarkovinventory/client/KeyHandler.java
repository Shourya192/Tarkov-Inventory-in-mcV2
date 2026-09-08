package com.tarkovinventory.client;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.network.C2SOpenTarkovPacket;
import com.tarkovinventory.network.ModNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = TarkovInventoryMod.MOD_ID, value = Dist.CLIENT)
public final class KeyHandler {

    public static final KeyMapping OPEN_INVENTORY = new KeyMapping(
            "key.tarkovinventory.open",
            GLFW.GLFW_KEY_I,
            "key.categories.tarkovinventory"
    );

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {

        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) return;
        if (mc.screen != null) return;

        if (OPEN_INVENTORY.consumeClick()) {
            ModNetwork.CHANNEL.sendToServer(new C2SOpenTarkovPacket());
        }
    }
}
