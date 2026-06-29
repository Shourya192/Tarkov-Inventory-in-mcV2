package com.tarkovinventory.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → Client: open the admin item-tagging screen. */
public class S2COpenAdminTagPacket {

    public S2COpenAdminTagPacket() {}

    public static void encode(S2COpenAdminTagPacket msg, FriendlyByteBuf buf) {}

    public static S2COpenAdminTagPacket decode(FriendlyByteBuf buf) {
        return new S2COpenAdminTagPacket();
    }

    public static void handle(S2COpenAdminTagPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                        () -> com.tarkovinventory.client.ClientPacketHandlers::openAdminTagScreen));
        ctx.get().setPacketHandled(true);
    }
}
