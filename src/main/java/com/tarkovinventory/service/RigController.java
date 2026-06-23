package com.tarkovinventory.service;

import com.tarkovinventory.inventory.RigInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class RigController {

    private RigController() {}

    public static ItemStack extract(ServerPlayer player, int slot, int amount) {

        ItemStack rig = RigSync.getRig(player);
        if (rig.isEmpty()) return ItemStack.EMPTY;

        // FIX: use public API
        RigInventory inv = RigService.loadPublic(rig);

        if (slot < 0 || slot >= inv.getSlots()) return ItemStack.EMPTY;

        ItemStack taken = inv.extractItem(slot, amount);
        if (taken.isEmpty()) return ItemStack.EMPTY;

        // FIX: use public API
        RigService.savePublic(rig, inv);
        RigSync.syncRig(player, rig);

        return taken;
    }

    public static ItemStack insert(ServerPlayer player, int slot, ItemStack stack) {

        ItemStack rig = RigSync.getRig(player);
        if (rig.isEmpty() || stack.isEmpty()) return stack;

        // FIX: use public API
        RigInventory inv = RigService.loadPublic(rig);

        if (slot < 0 || slot >= inv.getSlots()) return stack;

        ItemStack leftover = inv.insertItem(slot, stack);

        // FIX: use public API
        RigService.savePublic(rig, inv);
        RigSync.syncRig(player, rig);

        return leftover;
    }
}
