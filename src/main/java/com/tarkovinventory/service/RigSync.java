package com.tarkovinventory.service;

import com.tarkovinventory.compat.CuriosCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class RigSync {

    private RigSync() {}

    public static ItemStack getRig(ServerPlayer player) {

        if (CuriosCompat.isLoaded()) {
            ItemStack cur = CuriosCompat.getSlotItem(player, "body", 0);
            if (!cur.isEmpty()) return cur;
        }

        return player.getItemBySlot(EquipmentSlot.CHEST);
    }

    public static void syncRig(ServerPlayer player, ItemStack rig) {

        if (CuriosCompat.isLoaded()) {
            ItemStack cur = CuriosCompat.getSlotItem(player, "body", 0);

            if (ItemStack.isSameItemSameTags(cur, rig)) {
                CuriosCompat.setSlot(player, "body", 0, rig);
                sync(player);
                return;
            }
        }

        player.setItemSlot(EquipmentSlot.CHEST, rig);
        sync(player);
    }

    private static void sync(ServerPlayer player) {
        player.containerMenu.broadcastChanges();
        player.getInventory().setChanged();
    }
}
