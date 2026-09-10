package com.tarkovinventory.service;

import com.tarkovinventory.inventory.RigInventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public final class RigService {

    private RigService() {}

    // ─────────────────────────────
    // GET RIG ITEM
    // ─────────────────────────────

    public static ItemStack getRig(ServerPlayer player) {

        if (com.tarkovinventory.compat.CuriosCompat.isLoaded()) {
            ItemStack cur = com.tarkovinventory.compat.CuriosCompat.getSlotItem(player, "body", 0);
            if (!cur.isEmpty()) return cur;
        }

        return player.getItemBySlot(EquipmentSlot.CHEST);
    }

    // ─────────────────────────────
    // EXTRACT ITEM
    // ─────────────────────────────

    public static ItemStack extract(ServerPlayer player, ItemStack rig, int slot, int amount) {

        if (rig.isEmpty()) return ItemStack.EMPTY;

        RigInventory inv = load(rig);

        if (slot < 0 || slot >= inv.getSlots()) return ItemStack.EMPTY;

        ItemStack taken = inv.extractItem(slot, amount);
        if (taken.isEmpty()) return ItemStack.EMPTY;

        save(rig, inv);
        sync(player, rig);

        return taken;
    }

    // ─────────────────────────────
    // INSERT ITEM
    // ─────────────────────────────

    public static ItemStack insert(ServerPlayer player, ItemStack rig, int slot, ItemStack stack) {

        if (rig.isEmpty() || stack.isEmpty()) return stack;

        RigInventory inv = load(rig);

        if (slot < 0 || slot >= inv.getSlots()) return stack;

        ItemStack leftover = inv.insertItem(slot, stack);

        save(rig, inv);
        sync(player, rig);

        return leftover;
    }

    // ─────────────────────────────
    // LOAD / SAVE
    // ─────────────────────────────

    private static RigInventory load(ItemStack rig) {
        CompoundTag tag = rig.getOrCreateTag();

        if (tag.contains("TarkovRigInventory")) {
            return RigInventory.unwrapFromNBT(
                    tag.getCompound("TarkovRigInventory")
            );
        }

        return new RigInventory(3, 3);
    }

    private static void save(ItemStack rig, RigInventory inv) {
        CompoundTag tag = rig.getOrCreateTag();
        tag.put("TarkovRigInventory", inv.serializeNBT());
    }

    public static RigInventory loadPublic(ItemStack rig) {
    return load(rig);
}

public static void savePublic(ItemStack rig, RigInventory inv) {
    save(rig, inv);
}

    // ─────────────────────────────
    // SYNC BACK
    // ─────────────────────────────

    private static void sync(ServerPlayer player, ItemStack rig) {

        if (com.tarkovinventory.compat.CuriosCompat.isLoaded()) {
            ItemStack cur = com.tarkovinventory.compat.CuriosCompat.getSlotItem(player, "body", 0);

            if (ItemStack.isSameItemSameTags(cur, rig)) {
                com.tarkovinventory.compat.CuriosCompat.setSlot(player, "body", 0, rig);
                return;
            }
        }

        player.setItemSlot(EquipmentSlot.CHEST, rig);
    }
}
