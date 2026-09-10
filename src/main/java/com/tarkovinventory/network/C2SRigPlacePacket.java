package com.tarkovinventory.network;

import com.tarkovinventory.service.RigService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server
 * Place item into rig slot
 */
public class C2SRigPlacePacket {

    private final int slot;
    private final ItemStack stack;

    public C2SRigPlacePacket(int slot, ItemStack stack) {
        this.slot = slot;
        this.stack = stack;
    }

    public static void encode(C2SRigPlacePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.slot);
        buf.writeItem(msg.stack);
    }

    public static C2SRigPlacePacket decode(FriendlyByteBuf buf) {
        return new C2SRigPlacePacket(buf.readVarInt(), buf.readItem());
    }

    public static void handle(C2SRigPlacePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (msg.stack.isEmpty()) return;

            ItemStack rig = RigService.getRig(player);
            if (rig.isEmpty()) return;

            RigService.insert(player, rig, msg.slot, msg.stack.copy());
        });

        ctx.get().setPacketHandled(true);
    }
}
