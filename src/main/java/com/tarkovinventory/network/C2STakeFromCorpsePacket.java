package com.tarkovinventory.network;

import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Client → Server: take one item (or all) from a nearby corpse block.
 *
 * Three modes (determined by slot / namedSlot):
 *   slot == -1, namedSlot empty → take ALL (slotted + inventory)
 *   namedSlot non-empty         → take one equipment-slot item by key
 *   slot >= 0, namedSlot empty  → take inventory item at that index
 */
public class C2STakeFromCorpsePacket {

    private static final double MAX_RANGE_SQ = 10.0 * 10.0;

    private final BlockPos pos;
    private final int      slot;      // -1 = take-all; ≥0 = inventory index
    private final String   namedSlot; // non-empty = equipment-slot key

    private C2STakeFromCorpsePacket(BlockPos pos, int slot, String namedSlot) {
        this.pos       = pos;
        this.slot      = slot;
        this.namedSlot = namedSlot;
    }

    // ── Factory helpers ───────────────────────────────────────────────

    public static C2STakeFromCorpsePacket takeAll(BlockPos pos) {
        return new C2STakeFromCorpsePacket(pos, -1, "");
    }
    public static C2STakeFromCorpsePacket namedSlot(BlockPos pos, String key) {
        return new C2STakeFromCorpsePacket(pos, 0, key);
    }
    public static C2STakeFromCorpsePacket inventorySlot(BlockPos pos, int idx) {
        return new C2STakeFromCorpsePacket(pos, idx, "");
    }

    // ── Serialization ─────────────────────────────────────────────────

    public static void encode(C2STakeFromCorpsePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeVarInt(msg.slot);
        boolean hasNamed = !msg.namedSlot.isEmpty();
        buf.writeBoolean(hasNamed);
        if (hasNamed) buf.writeUtf(msg.namedSlot, 64);
    }

    public static C2STakeFromCorpsePacket decode(FriendlyByteBuf buf) {
        BlockPos pos  = buf.readBlockPos();
        int slot      = buf.readVarInt();
        boolean named = buf.readBoolean();
        String key    = named ? buf.readUtf(64) : "";
        return new C2STakeFromCorpsePacket(pos, slot, key);
    }

    // ── Server handler ────────────────────────────────────────────────

    public static void handle(C2STakeFromCorpsePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.level() instanceof ServerLevel level)) return;
            if (msg.pos.distSqr(player.blockPosition()) > MAX_RANGE_SQ) return;
            if (!(level.getBlockEntity(msg.pos) instanceof TarkovCorpseBlockEntity be)) return;

            if (msg.slot == -1) {
                List<ItemStack> taken = be.takeAll();
                taken.forEach(s -> com.tarkovinventory.inventory.TarkovItemRouter.store(player, s));
            } else if (!msg.namedSlot.isEmpty()) {
                ItemStack taken = be.takeSlottedItem(msg.namedSlot);
                if (!taken.isEmpty()) com.tarkovinventory.inventory.TarkovItemRouter.store(player, taken);
            } else {
                ItemStack taken = be.takeInventoryItem(msg.slot);
                if (!taken.isEmpty()) com.tarkovinventory.inventory.TarkovItemRouter.store(player, taken);
            }

            if (be.isEmpty()) {
                level.removeBlock(msg.pos, false);
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2CCorpseContentsPacket(msg.pos, "", Map.of(), List.of()));
            } else {
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new S2CCorpseContentsPacket(msg.pos, be.getOwnerName(),
                                be.getSlottedItems(), be.getInventoryItems()));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void giveOrDrop(ServerPlayer player, ServerLevel level, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            level.addFreshEntity(new ItemEntity(
                    level, player.getX(), player.getY(), player.getZ(), stack));
        }
    }
}
