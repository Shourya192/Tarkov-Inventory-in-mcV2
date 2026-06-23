package com.tarkovinventory.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of nearby corpse block inventories.
 * Populated/cleared by S2CCorpseContentsPacket; read by TarkovInventoryScreen.
 * Uses ConcurrentHashMap: network thread writes, render thread reads.
 */
@OnlyIn(Dist.CLIENT)
public final class CorpseClientCache {

    private CorpseClientCache() {}

    /**
     * Snapshot of a corpse's contents.
     *
     * @param slottedItems  equipment/curios slots keyed by slot-id ("armor.head", "curios.back", …)
     * @param inventoryItems remaining main-inventory stacks
     */
    public record CorpseEntry(
            String ownerName,
            Map<String, ItemStack> slottedItems,
            List<ItemStack> inventoryItems
    ) {
        /** Total item count across both collections. */
        public int totalCount() { return slottedItems.size() + inventoryItems.size(); }
        /** True when both maps/lists are empty (corpse fully looted). */
        public boolean isEmpty() { return slottedItems.isEmpty() && inventoryItems.isEmpty(); }
    }

    private static final ConcurrentHashMap<BlockPos, CorpseEntry> CORPSES = new ConcurrentHashMap<>();

    public static void put(BlockPos pos, String ownerName,
                           Map<String, ItemStack> slotted, List<ItemStack> inventory) {
        CORPSES.put(pos, new CorpseEntry(ownerName,
                Map.copyOf(slotted), List.copyOf(inventory)));
    }

    public static void remove(BlockPos pos)      { CORPSES.remove(pos); }
    public static Map<BlockPos, CorpseEntry> all() { return Map.copyOf(CORPSES); }
    public static void clear()                   { CORPSES.clear(); }
}
