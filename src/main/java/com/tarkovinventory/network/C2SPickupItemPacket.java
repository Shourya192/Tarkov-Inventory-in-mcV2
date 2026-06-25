package com.tarkovinventory.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Sent client → server when the player left-clicks an item in the Vicinity panel.
 *
 * The server validates range, adds the item to the player's inventory, and
 * discards the ItemEntity.
 *
 * Use C2SLootAllPacket to pick up every nearby item in one shot.
 */
public class C2SPickupItemPacket {

    /** Maximum distance (blocks) the item entity may be from the player. */
    private static final double MAX_RANGE = 6.0;

    private final int entityId;

    public C2SPickupItemPacket(int entityId) {
        this.entityId = entityId;
    }

    public static void encode(C2SPickupItemPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
    }

    public static C2SPickupItemPacket decode(FriendlyByteBuf buf) {
        return new C2SPickupItemPacket(buf.readInt());
    }

    public static void handle(C2SPickupItemPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity entity = player.level().getEntity(msg.entityId);
            if (!(entity instanceof ItemEntity itemEntity)) return;
            if (!itemEntity.isAlive()) return;
            if (itemEntity.distanceTo(player) > MAX_RANGE) return;

            ItemStack stack = itemEntity.getItem().copy();
            int before = stack.getCount();
            com.tarkovinventory.inventory.TarkovItemRouter.store(player, stack);
            // store() drops any remainder; the original entity is always consumed
            itemEntity.discard();
            player.playSound(
                SoundEvents.ITEM_PICKUP, 0.2f,
                ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7f + 1.0f) * 2.0f
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
