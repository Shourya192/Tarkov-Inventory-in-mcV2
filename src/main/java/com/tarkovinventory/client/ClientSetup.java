package com.tarkovinventory.client;

import com.tarkovinventory.TarkovInventoryMod;
import net.minecraftforge.api.distmarker.Dist;
import com.tarkovinventory.client.screen.TarkovInventoryScreen;
import com.tarkovinventory.registry.ModMenuTypes;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import com.tarkovinventory.client.render.TarkovCorpseRenderer;
import com.tarkovinventory.client.render.TarkovEquipmentLayer;
import com.tarkovinventory.registry.ModBlockEntities;
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
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                ModBlockEntities.TARKOV_CORPSE.get(), TarkovCorpseRenderer::new);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            try {
                @SuppressWarnings("unchecked")
                net.minecraft.client.renderer.entity.LivingEntityRenderer<
                    net.minecraft.client.player.AbstractClientPlayer,
                    net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer>
                > renderer = (net.minecraft.client.renderer.entity.LivingEntityRenderer<
                    net.minecraft.client.player.AbstractClientPlayer,
                    net.minecraft.client.model.PlayerModel<net.minecraft.client.player.AbstractClientPlayer>
                >) event.getSkin(skin);
                if (renderer != null) {
                    renderer.addLayer(new TarkovEquipmentLayer(renderer));
                }
            } catch (ClassCastException ignored) {
                // Skin uses a non-PlayerModel renderer — skip
            }
        }
    }

    @SubscribeEvent
    public static void onClientSetup(net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenuTypes.TARKOV_INVENTORY.get(), TarkovInventoryScreen::new));
    }
}
