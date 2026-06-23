package com.tarkovinventory.client;

import com.tarkovinventory.TarkovInventoryMod;
import net.minecraftforge.api.distmarker.Dist;
import com.tarkovinventory.client.screen.TarkovInventoryScreen;
import com.tarkovinventory.registry.ModMenuTypes;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLEAN CLIENT SETUP (STANDALONE UI VERSION)
 * - registers the Tarkov menu screen
 * - registers keybinds
 */
@Mod.EventBusSubscriber(
        modid = TarkovInventoryMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class ClientSetup {

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyHandler.OPEN_INVENTORY);
    }

    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.TARKOV_INVENTORY.get(), TarkovInventoryScreen::new));
    }
}
