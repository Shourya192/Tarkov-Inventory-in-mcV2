package com.tarkovinventory.network;

import com.tarkovinventory.service.RigService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server
 * Take item from rig slot
 */
public class C2SRigSlotPacket {

    private final int slot;

    public C2SRigSlotPacket(int slot) {
        this.slot = slot;
    }

    public static void encode(C2SRigSlotPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slot);
    }

    public static C2SRigSlotPacket decode(FriendlyByteBuf buf) {
        return new C2SRigSlotPacket(buf.readVarInt());
    }

    public static void handle(C2SRigSlotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ItemStack rig = RigService.getRig(player);
            if (rig.isEmpty()) return;

            RigService.extract(player, rig, msg.slot, 64);
        });

        ctx.get().setPacketHandled(true);
    }
}
