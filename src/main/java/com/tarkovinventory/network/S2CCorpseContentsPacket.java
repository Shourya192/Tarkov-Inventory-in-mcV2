package com.tarkovinventory.network;

import com.tarkovinventory.client.CorpseClientCache;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkEvent;

import java.util.*;
import java.util.function.Supplier;

/**
 * Server → Client: delivers the current structured contents of a nearby corpse block.
 *
 * Empty slottedItems + empty inventoryItems signals full loot — cache entry is removed.
 */
public class S2CCorpseContentsPacket {

    private final BlockPos              pos;
    private final String                ownerName;
    private final Map<String, ItemStack> slottedItems;
    private final List<ItemStack>        inventoryItems;

    public S2CCorpseContentsPacket(BlockPos pos, String ownerName,
                                    Map<String, ItemStack> slotted,
                                    List<ItemStack> inventory) {
        this.pos            = pos;
        this.ownerName      = ownerName;
        this.slottedItems   = slotted;
        this.inventoryItems = inventory;
    }

    public static void encode(S2CCorpseContentsPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeUtf(msg.ownerName, 64);
        // Slotted
        buf.writeVarInt(msg.slottedItems.size());
        msg.slottedItems.forEach((k, v) -> { buf.writeUtf(k, 64); buf.writeItem(v); });
        // Inventory
        buf.writeVarInt(msg.inventoryItems.size());
        for (ItemStack s : msg.inventoryItems) buf.writeItem(s);
    }

    public static S2CCorpseContentsPacket decode(FriendlyByteBuf buf) {
        BlockPos pos      = buf.readBlockPos();
        String ownerName  = buf.readUtf(64);
        int sc = buf.readVarInt();
        Map<String, ItemStack> slotted = new LinkedHashMap<>(sc);
        for (int i = 0; i < sc; i++) slotted.put(buf.readUtf(64), buf.readItem());
        int ic = buf.readVarInt();
        List<ItemStack> inventory = new ArrayList<>(ic);
        for (int i = 0; i < ic; i++) inventory.add(buf.readItem());
        return new S2CCorpseContentsPacket(pos, ownerName, slotted, inventory);
    }

    @OnlyIn(Dist.CLIENT)
    public static void handle(S2CCorpseContentsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.slottedItems.isEmpty() && msg.inventoryItems.isEmpty()) {
                CorpseClientCache.remove(msg.pos);
            } else {
                CorpseClientCache.put(msg.pos, msg.ownerName,
                        msg.slottedItems, msg.inventoryItems);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
