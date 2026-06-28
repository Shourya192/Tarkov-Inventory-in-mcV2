package com.tarkovinventory.network;

import com.tarkovinventory.config.ItemTypeConfig;
import com.tarkovinventory.inventory.RigSizes;
import com.tarkovinventory.inventory.BackpackSizes;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: admin assigns a slot type (and optionally grid size) to an item.
 * Requires permission level 2 (op). Persists to config server-wide.
 *
 * type: slot type name (RIG, BACKPACK, FACE, EAR, HEAD, ARMOR, PANTS, BOOTS, KNEE, NONE)
 * cols/rows: grid size, only used when type is RIG or BACKPACK (0 = leave unchanged)
 */
public class C2SSetItemTypePacket {

    private final String itemId;
    private final String type;
    private final int cols;
    private final int rows;

    public C2SSetItemTypePacket(String itemId, String type, int cols, int rows) {
        this.itemId = itemId;
        this.type = type;
        this.cols = cols;
        this.rows = rows;
    }

    public static void encode(C2SSetItemTypePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.itemId);
        buf.writeUtf(msg.type);
        buf.writeVarInt(msg.cols);
        buf.writeVarInt(msg.rows);
    }

    public static C2SSetItemTypePacket decode(FriendlyByteBuf buf) {
        return new C2SSetItemTypePacket(
                buf.readUtf(), buf.readUtf(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(C2SSetItemTypePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // OP gate: only level-2+ operators may change item types.
            if (!player.hasPermissions(2)) {
                player.displayClientMessage(
                        Component.literal("§cYou don't have permission to assign item types."), false);
                return;
            }
            if (msg.itemId == null || msg.itemId.isEmpty()) return;

            ItemTypeConfig.setType(msg.itemId, msg.type);

            // For rigs/backpacks, also register the grid size if provided.
            if (msg.cols > 0 && msg.rows > 0) {
                if ("RIG".equalsIgnoreCase(msg.type)) {
                    RigSizes.registerOverride(msg.itemId, msg.cols, msg.rows);
                } else if ("BACKPACK".equalsIgnoreCase(msg.type)) {
                    BackpackSizes.registerOverride(msg.itemId, msg.cols, msg.rows);
                }
            }

            String label = (msg.type == null || msg.type.equalsIgnoreCase("NONE"))
                    ? "§7cleared" : "§f" + msg.type.toUpperCase();
            String sizeInfo = (msg.cols > 0 && msg.rows > 0
                    && ("RIG".equalsIgnoreCase(msg.type) || "BACKPACK".equalsIgnoreCase(msg.type)))
                    ? " §7(" + msg.cols + "×" + msg.rows + ")" : "";
            player.displayClientMessage(
                    Component.literal("§aSet §f" + msg.itemId + "§a → " + label + sizeInfo
                            + " §7(saved)"), false);
        });
        ctx.get().setPacketHandled(true);
    }
}
