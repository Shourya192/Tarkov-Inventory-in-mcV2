package com.tarkovinventory.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of nearby loot sources (corpses or opened containers).
 * Populated/cleared by S2CCorpseContentsPacket; read by TarkovInventoryScreen.
 */
@OnlyIn(Dist.CLIENT)
public final class CorpseClientCache {

    private CorpseClientCache() {}

    /**
     * Snapshot of a loot source's contents.
     *
     * @param isCorpse true  → TarkovCorpseBlockEntity (use C2STakeFromCorpsePacket)
     *                 false → generic Container block  (use C2STakeFromContainerPacket)
     */
    public record CorpseEntry(
            String ownerName,
            Map<String, ItemStack> slottedItems,
            List<ItemStack> inventoryItems,
            boolean isCorpse
    ) {
        public int totalCount()  { return slottedItems.size() + inventoryItems.size(); }
        public boolean isEmpty() { return slottedItems.isEmpty() && inventoryItems.isEmpty(); }
    }

    private static final ConcurrentHashMap<BlockPos, CorpseEntry> CORPSES = new ConcurrentHashMap<>();

    public static void put(BlockPos pos, String ownerName,
                           Map<String, ItemStack> slotted, List<ItemStack> inventory,
                           boolean isCorpse) {
        CORPSES.put(pos, new CorpseEntry(ownerName,
                Map.copyOf(slotted), List.copyOf(inventory), isCorpse));
    }

    /** Back-compat — assumes source is a corpse. */
    public static void put(BlockPos pos, String ownerName,
                           Map<String, ItemStack> slotted, List<ItemStack> inventory) {
        put(pos, ownerName, slotted, inventory, true);
    }

    public static void remove(BlockPos pos)        { CORPSES.remove(pos); }
    public static Map<BlockPos, CorpseEntry> all() { return Map.copyOf(CORPSES); }
    public static void clear()                     { CORPSES.clear(); }
}
