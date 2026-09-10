package com.tarkovinventory.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Sent client → server when the player clicks "LOOT ALL" in the Vicinity panel.
 * Server picks up every ItemEntity within range and adds them to the player's inventory.
 */
public class C2SLootAllPacket {

    private static final double RANGE = 6.0;

    public C2SLootAllPacket() {}

    public static void encode(C2SLootAllPacket msg, FriendlyByteBuf buf) {}

    public static C2SLootAllPacket decode(FriendlyByteBuf buf) {
        return new C2SLootAllPacket();
    }

    public static void handle(C2SLootAllPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            AABB box = player.getBoundingBox().inflate(RANGE);
            List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && e.distanceTo(player) <= RANGE);

            boolean pickedAny = false;
            for (ItemEntity itemEntity : items) {
                ItemStack stack = itemEntity.getItem().copy();
                if (player.getInventory().add(stack)) {
                    itemEntity.discard();
                    pickedAny = true;
                }
            }

            if (pickedAny) {
                player.playSound(
                    SoundEvents.ITEM_PICKUP, 0.4f,
                    ((player.getRandom().nextFloat() - player.getRandom().nextFloat()) * 0.7f + 1.0f) * 2.0f
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
