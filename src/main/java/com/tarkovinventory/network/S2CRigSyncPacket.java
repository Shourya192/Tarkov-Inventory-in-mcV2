package com.tarkovinventory.network;

import com.tarkovinventory.client.screen.TarkovInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → Client: sync rig or backpack inventory contents.
 * Sent when the menu is opened or when rig/backpack contents change.
 */
public class S2CRigSyncPacket {

    private final int mode; // 0=rig, 1=backpack
    private final CompoundTag data;
    private final int cols;
    private final int rows;

    public S2CRigSyncPacket(int mode, CompoundTag data, int cols, int rows) {
        this.mode = mode;
        this.data = data;
        this.cols = cols;
        this.rows = rows;
    }

    public static void encode(S2CRigSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode);
        buf.writeNbt(msg.data);
        buf.writeVarInt(msg.cols);
        buf.writeVarInt(msg.rows);
    }

    public static S2CRigSyncPacket decode(FriendlyByteBuf buf) {
        return new S2CRigSyncPacket(
                buf.readVarInt(),
                buf.readNbt(),
                buf.readVarInt(),
                buf.readVarInt()
        );
    }

    public static void handle(S2CRigSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof TarkovInventoryScreen screen) {
                screen.applyRigSync(msg.mode, msg.data, msg.cols, msg.rows);
            }
        });

        ctx.get().setPacketHandled(true);
    }
}
