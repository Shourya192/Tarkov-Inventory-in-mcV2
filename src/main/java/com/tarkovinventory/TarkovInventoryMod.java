package com.tarkovinventory;

import com.tarkovinventory.capability.ModCapabilities;
import com.tarkovinventory.command.TarkovCommand;
import com.tarkovinventory.compat.TaczCompat;
import com.tarkovinventory.network.ModNetwork;
import com.tarkovinventory.registry.ModBlockEntities;
import com.tarkovinventory.registry.ModBlocks;
import com.tarkovinventory.registry.ModItems;
import com.tarkovinventory.registry.ModMenuTypes;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.tarkovinventory.inventory.RigSizes;
import com.tarkovinventory.inventory.BackpackSizes;

@Mod(TarkovInventoryMod.MOD_ID)
public class TarkovInventoryMod {

    public static final String MOD_ID = "tarkovinventory";

    public TarkovInventoryMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // ── Registry ─────────────────────────────────────────────────
        ModItems.ITEMS.register(modEventBus);
        ModMenuTypes.MENU_TYPES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);

        // ── Lifecycle ─────────────────────────────────────────────────
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        // RegisterCapabilitiesEvent fires on the MOD bus — register it here explicitly.
        // NOTE: do NOT also call MinecraftForge.EVENT_BUS.register(ModCapabilities.class)
        // because @Mod.EventBusSubscriber on that class already handles the FORGE bus events
        // (AttachCapabilitiesEvent). Double-registering causes duplicate capability attachment.
        modEventBus.addListener(ModCapabilities::onRegisterCapabilities);

        MinecraftForge.EVENT_BUS.register(this);
        // PlayerDeathHandler and ModCapabilities Forge-bus events are handled
        // automatically via @Mod.EventBusSubscriber annotations.
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ModNetwork.register();
            TaczCompat.registerSizes();
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        TarkovCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        // Load persisted rig/backpack size overrides from the server config dir.
        java.nio.file.Path configDir = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();
        RigSizes.initConfig(configDir);
        BackpackSizes.initConfig(configDir);
        com.tarkovinventory.config.ItemTypeConfig.initConfig(configDir);
    }
}
