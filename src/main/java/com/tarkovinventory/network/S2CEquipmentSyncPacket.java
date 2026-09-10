package com.tarkovinventory.network;

import com.tarkovinventory.capability.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: full equipment-capability state (custom slots + grid).
 *
 * Without this, custom slots (FACE, EAR, RIG, KNEES, BACKPACK, ...) set on the
 * server never appear on the client, so they look empty after reopening the UI.
 */
public class S2CEquipmentSyncPacket {

    private final CompoundTag data;

    public S2CEquipmentSyncPacket(CompoundTag data) {
        this.data = data;
    }

    public static void encode(S2CEquipmentSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }

    public static S2CEquipmentSyncPacket decode(FriendlyByteBuf buf) {
        return new S2CEquipmentSyncPacket(buf.readNbt());
    }

    @OnlyIn(Dist.CLIENT)
    public static void handle(S2CEquipmentSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (Minecraft.getInstance().player == null) return;
            ModCapabilities.get(Minecraft.getInstance().player).ifPresent(cap -> {
                if (msg.data != null) cap.deserializeNBT(msg.data);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
