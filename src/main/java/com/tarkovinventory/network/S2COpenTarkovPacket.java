package com.tarkovinventory.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2COpenTarkovPacket {

    public static void encode(S2COpenTarkovPacket msg, FriendlyByteBuf buf) {}

    public static S2COpenTarkovPacket decode(FriendlyByteBuf buf) {
        return new S2COpenTarkovPacket();
    }

    public static void handle(S2COpenTarkovPacket msg, Supplier<NetworkEvent.Context> ctx) {

        // Opening is now performed server-side with NetworkHooks.openScreen so
        // the client receives a real AbstractContainerMenu. This legacy packet
        // remains registered for protocol compatibility and intentionally does
        // not create a standalone client-only screen.
        ctx.get().setPacketHandled(true);
    }
}
