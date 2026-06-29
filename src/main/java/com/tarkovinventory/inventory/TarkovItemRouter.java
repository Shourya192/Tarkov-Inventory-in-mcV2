package com.tarkovinventory.inventory;

import com.tarkovinventory.capability.IPlayerEquipment;
import com.tarkovinventory.capability.ModCapabilities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Routes looted/picked-up items into the player's Tarkov storage in priority
 * order, NEVER into the vanilla main inventory:
 *
 *   1. Backpack grid (capability GridInventory) — if a backpack is equipped
 *   2. Rig container                            — if a rig is equipped
 *   3. Pockets (hotbar slots 2–8)
 *   4. Drop on the ground (only if everything above is full)
 *
 * Weapons going to PRIMARY/SECONDARY (hotbar 0/1) are left to the player to
 * place manually; loot does not auto-equip weapons.
 */
public final class TarkovItemRouter {

    private TarkovItemRouter() {}

    /** Number of pocket slots backed by hotbar indices 2..8. */
    private static final int POCKET_START = 2;
    private static final int POCKET_END   = 9; // exclusive

    /**
     * Attempt to store {@code stack} in Tarkov storage. Mutates the stack's
     * count as items are consumed. Any remainder is dropped at the player.
     */
    public static void store(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        ServerLevel level = (ServerLevel) player.level();

        IPlayerEquipment cap = ModCapabilities.get(player).orElse(null);

        // 1. Backpack — only if a backpack is equipped; store in item NBT via RigContainer
        if (cap != null && !cap.getSlot(IPlayerEquipment.SLOT_ON_BACK).isEmpty()) {
            RigContainer bp = new RigContainer(player, RigContainer.Mode.BACKPACK);
            for (int i = 0; i < bp.getContainerSize() && !stack.isEmpty(); i++) {
                ItemStack cur = bp.getItem(i);
                if (cur.isEmpty()) {
                    bp.setItem(i, stack.copy()); stack.setCount(0);
                } else if (ItemStack.isSameItemSameTags(cur, stack)) {
                    int move = Math.min(cur.getMaxStackSize() - cur.getCount(), stack.getCount());
                    if (move > 0) { cur.grow(move); stack.shrink(move); bp.setItem(i, cur); }
                }
            }
        }

        // 2. Rig — only if a rig is equipped
        if (!stack.isEmpty() && cap != null
                && !cap.getSlot(IPlayerEquipment.SLOT_RIG).isEmpty()) {
            RigContainer rig = new RigContainer(player, RigContainer.Mode.RIG);
            rig.reloadFromItem();
            for (int i = 0; i < rig.getContainerSize() && !stack.isEmpty(); i++) {
                ItemStack cur = rig.getItem(i);
                if (cur.isEmpty()) {
                    rig.setItem(i, stack.copy());
                    stack.setCount(0);
                } else if (ItemStack.isSameItemSameTags(cur, stack)) {
                    int room = cur.getMaxStackSize() - cur.getCount();
                    int move = Math.min(room, stack.getCount());
                    if (move > 0) {
                        cur.grow(move);
                        stack.shrink(move);
                        rig.setItem(i, cur);
                    }
                }
            }
        }

        // 3. Pockets (hotbar 2–8)
        if (!stack.isEmpty()) {
            for (int i = POCKET_START; i < POCKET_END && !stack.isEmpty(); i++) {
                ItemStack cur = player.getInventory().getItem(i);
                if (cur.isEmpty()) {
                    player.getInventory().setItem(i, stack.copy());
                    stack.setCount(0);
                } else if (ItemStack.isSameItemSameTags(cur, stack)) {
                    int room = cur.getMaxStackSize() - cur.getCount();
                    int move = Math.min(room, stack.getCount());
                    if (move > 0) {
                        cur.grow(move);
                        stack.shrink(move);
                    }
                }
            }
        }

        // 4. Drop remainder on the ground (never vanilla main inventory)
        if (!stack.isEmpty()) {
            ItemEntity drop = new ItemEntity(level,
                    player.getX(), player.getY(), player.getZ(), stack.copy());
            drop.setPickUpDelay(20);
            level.addFreshEntity(drop);
            stack.setCount(0);
        }
    }
}
