package com.tarkovinventory.network;

import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client → Server: take one slot (or all slots) from a generic block Container
 * (chest, barrel, dispenser, etc.) that was opened via ContainerInterceptHandler.
 *
 * slot == -1 means "take all".
 */
public class C2STakeFromContainerPacket {

    private final BlockPos pos;
    private final int slot; // -1 = take all

    private C2STakeFromContainerPacket(BlockPos pos, int slot) {
        this.pos  = pos;
        this.slot = slot;
    }

    public static C2STakeFromContainerPacket takeAll(BlockPos pos) {
        return new C2STakeFromContainerPacket(pos, -1);
    }

    public static C2STakeFromContainerPacket inventorySlot(BlockPos pos, int slot) {
        return new C2STakeFromContainerPacket(pos, slot);
    }

    // ── Codec ────────────────────────────────────────────────────────

    public static void encode(C2STakeFromContainerPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.slot);
    }

    public static C2STakeFromContainerPacket decode(FriendlyByteBuf buf) {
        return new C2STakeFromContainerPacket(buf.readBlockPos(), buf.readInt());
    }

    // ── Handler (server side) ─────────────────────────────────────────

    public static void handle(C2STakeFromContainerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel level)) return;

            // Safety: reject if the block is a Tarkov corpse (wrong packet) or too far
            if (level.getBlockEntity(msg.pos) instanceof TarkovCorpseBlockEntity) return;
            if (msg.pos.distSqr(player.blockPosition()) > 10 * 10) return;

            if (!(level.getBlockEntity(msg.pos) instanceof Container container)) return;

            if (msg.slot == -1) {
                // Take ALL non-empty items
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack s = container.getItem(i);
                    if (!s.isEmpty()) {
                        giveOrDrop(player, s.copy());
                        container.setItem(i, ItemStack.EMPTY);
                    }
                }
                // Clear cache entry on client
                ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CCorpseContentsPacket(msg.pos, "", Map.of(), List.of(), false));
            } else {
                // Take one slot
                if (msg.slot >= container.getContainerSize()) return;
                ItemStack s = container.getItem(msg.slot);
                if (s.isEmpty()) return;
                giveOrDrop(player, s.copy());
                container.setItem(msg.slot, ItemStack.EMPTY);
                container.setChanged();

                // Send refreshed contents back to client
                List<ItemStack> remaining = new ArrayList<>();
                for (int i = 0; i < container.getContainerSize(); i++) {
                    remaining.add(container.getItem(i).copy());
                }
                String name = level.getBlockState(msg.pos).getBlock().getName().getString();
                ModNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new S2CCorpseContentsPacket(msg.pos, name, Map.of(), remaining, false));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
