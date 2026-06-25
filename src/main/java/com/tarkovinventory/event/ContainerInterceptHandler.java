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

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getSide().isClient()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = event.getPos();

        // ── Corpse blocks ─────────────────────────────────────────────────
        // Handled here via the event system (more reliable than Block.use()
        // on mobile where the thin hitbox can be hard to aim at).
        if (level.getBlockEntity(pos) instanceof TarkovCorpseBlockEntity) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(false));
            openLoot(player, pos.immutable(), true);
            return;
        }

        // ── Generic Container blocks (chest, barrel, etc.) ─────────────────
        if (!(level.getBlockEntity(pos) instanceof Container)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(false));
        openLoot(player, pos.immutable(), false);
    }

    static void openLoot(ServerPlayer player, BlockPos pos, boolean isCorpse) {
        // Safely return any cursor item to inventory before closing current menu
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            if (!player.getInventory().add(carried.copy())) player.drop(carried.copy(), false);
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
        player.closeContainer();
        NetworkHooks.openScreen(player,
                new TarkovInventoryMenu.LootMenuProvider(pos, isCorpse),
                buf -> TarkovInventoryMenu.writeDimensions(buf, player, pos, isCorpse));
    }

    /** Sync capability to client on login so equipment renders on player model. */
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
