package com.tarkovinventory.event;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import com.tarkovinventory.container.TarkovInventoryMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/**
 * Intercepts right-clicks on Container block entities (chests, barrels, etc.)
 * and opens the Tarkov inventory with the container's items in the LOOT panel.
 */
@Mod.EventBusSubscriber(modid = TarkovInventoryMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ContainerInterceptHandler {

    private ContainerInterceptHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isClient()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = event.getPos();

        // Skip non-containers and our own corpse block
        if (!(level.getBlockEntity(pos) instanceof Container)) return;
        if (level.getBlockEntity(pos) instanceof TarkovCorpseBlockEntity) return;

        BlockPos immPos = pos.immutable();

        if (!(player.containerMenu instanceof TarkovInventoryMenu)) {
            NetworkHooks.openScreen(player,
                    new TarkovInventoryMenu.LootMenuProvider(immPos, false),
                    buf -> TarkovInventoryMenu.writeDimensions(buf, player, immPos, false));
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(false));
    }
}
