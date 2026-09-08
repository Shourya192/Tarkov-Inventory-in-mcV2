package com.tarkovinventory.network;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server → Client: sent to all players who have the same loot source open
 * when another player takes or moves an item. Keeps all viewers in sync.
 */
public class S2CLootSyncPacket {

    private final BlockPos pos;
    private final List<ItemStack> items;

    public S2CLootSyncPacket(BlockPos pos, List<ItemStack> items) {
        this.pos   = pos;
        this.items = items;
    }

    public static void encode(S2CLootSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.items.size());
        for (ItemStack s : msg.items) buf.writeItem(s);
    }

    public static S2CLootSyncPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int size = buf.readVarInt();
        List<ItemStack> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) items.add(buf.readItem());
        return new S2CLootSyncPacket(pos, items);
    }

    @OnlyIn(Dist.CLIENT)
    public static void handle(S2CLootSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (!(mc.screen instanceof com.tarkovinventory.client.screen.TarkovInventoryScreen)) return;
            var screen = (com.tarkovinventory.client.screen.TarkovInventoryScreen) mc.screen;
            var menu = (com.tarkovinventory.container.TarkovInventoryMenu) screen.getMenu();
            if (!msg.pos.equals(menu.activeLootPos)) return;
            // Apply the updated loot state — another player took/moved something
            for (int i = 0; i < Math.min(msg.items.size(), com.tarkovinventory.container.TarkovInventoryMenu.LOOT_SLOTS); i++) {
                menu.updateLootSlot(i, msg.items.get(i));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
