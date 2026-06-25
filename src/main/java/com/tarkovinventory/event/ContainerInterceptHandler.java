package com.tarkovinventory.event;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import com.tarkovinventory.capability.ModCapabilities;
import com.tarkovinventory.container.TarkovInventoryMenu;
import com.tarkovinventory.network.ModNetwork;
import com.tarkovinventory.network.S2CEquipmentSyncPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = TarkovInventoryMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ContainerInterceptHandler {

    private ContainerInterceptHandler() {}

    /**
     * Intercepts right-clicks on Container blocks and opens them inside the
     * Tarkov LOOT panel instead of the vanilla container screen.
     *
     * If the Tarkov inventory is already open, we close it first so the new
     * loot source is correctly loaded into fresh slot positions.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isClient()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = event.getPos();

        if (!(level.getBlockEntity(pos) instanceof Container)) return;
        if (level.getBlockEntity(pos) instanceof TarkovCorpseBlockEntity) return;

        // Cancel vanilla container open FIRST
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(false));

        BlockPos immPos = pos.immutable();

        // Return any cursor item to inventory before closing current menu
        if (!player.containerMenu.getCarried().isEmpty()) {
            ItemStack carried = player.containerMenu.getCarried().copy();
            if (!player.getInventory().add(carried)) player.drop(carried, false);
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }

        // Close whatever is open (including a previous Tarkov menu) and open fresh
        player.closeContainer();
        NetworkHooks.openScreen(player,
                new TarkovInventoryMenu.LootMenuProvider(immPos, false),
                buf -> TarkovInventoryMenu.writeDimensions(buf, player, immPos, false));
    }

    /**
     * Syncs the equipment capability to the client on login so that custom
     * equipment (headset, knee pads, etc.) is visible on the player model
     * even before the inventory is first opened.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ModCapabilities.get(sp).ifPresent(cap ->
            ModNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> sp),
                new S2CEquipmentSyncPacket(cap.serializeNBT())
            )
        );
    }
}
