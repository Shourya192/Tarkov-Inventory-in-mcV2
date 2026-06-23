package com.tarkovinventory.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import com.tarkovinventory.container.TarkovInventoryMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class C2SOpenTarkovPacket {

    public static void encode(C2SOpenTarkovPacket msg, FriendlyByteBuf buf) {}

    public static C2SOpenTarkovPacket decode(FriendlyByteBuf buf) {
        return new C2SOpenTarkovPacket();
    }

    public static void handle(C2SOpenTarkovPacket msg, Supplier<NetworkEvent.Context> ctx) {

        ctx.get().enqueueWork(() -> {

            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            NetworkHooks.openScreen(player, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.literal("Tarkov Inventory");
                }

                @Override
                public @NotNull AbstractContainerMenu createMenu(int windowId, @NotNull Inventory inv, @NotNull Player p) {
                    return new TarkovInventoryMenu(windowId, inv, 0);
                }
            }, buf -> buf.writeInt(0));
        });

        ctx.get().setPacketHandled(true);
    }
}
