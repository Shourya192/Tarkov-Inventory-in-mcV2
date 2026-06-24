package com.tarkovinventory.event;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import com.tarkovinventory.container.TarkovInventoryMenu;
import com.tarkovinventory.network.ModNetwork;
import com.tarkovinventory.network.S2CCorpseContentsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Intercepts right-click interactions with Container block entities
 * (chests, barrels, dispensers, etc.) and redirects them into the
 * Tarkov inventory screen's LOOT panel instead of opening the vanilla UI.
 *
 * Flow:
 *   1. Player right-clicks a container block.
 *   2. Event is cancelled (no vanilla screen opens).
 *   3. Server reads the container's contents and sends them to the client
 *      via S2CCorpseContentsPacket (isCorpse=false).
 *   4. The Tarkov inventory screen is opened (or re-used if already open).
 *   5. The LOOT panel shows the container contents.
 *   6. Clicking items in the LOOT panel sends C2STakeFromContainerPacket.
 */
@Mod.EventBusSubscriber(modid = TarkovInventoryMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ContainerInterceptHandler {

    private ContainerInterceptHandler() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Only server-side, main hand
        if (event.getSide().isClient()) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = event.getPos();

        // Only intercept actual Container block entities —
        // skip our own corpse (handled by C2STakeFromCorpsePacket)
        if (!(level.getBlockEntity(pos) instanceof Container container)) return;
        if (level.getBlockEntity(pos) instanceof TarkovCorpseBlockEntity) return;

        // Read current container contents
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            items.add(container.getItem(i).copy());
        }

        String displayName = level.getBlockState(pos).getBlock().getName().getString();

        // Send contents to client (isCorpse=false → will use C2STakeFromContainerPacket)
        ModNetwork.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new S2CCorpseContentsPacket(pos.immutable(), displayName,
                Map.of(), items, false));

        // Open (or refresh) Tarkov inventory
        if (!(player.containerMenu instanceof TarkovInventoryMenu)) {
            NetworkHooks.openScreen(player, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.literal("Tarkov Inventory");
                }

                @Override
                public @NotNull AbstractContainerMenu createMenu(
                        int id, @NotNull Inventory inv, @NotNull Player p) {
                    return new TarkovInventoryMenu(id, inv, 0);
                }
            }, buf -> TarkovInventoryMenu.writeDimensions(buf, player));
        }

        // Cancel vanilla container open
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(false));
    }
}
