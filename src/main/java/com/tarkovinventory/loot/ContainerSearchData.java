package com.tarkovinventory.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent per-container, per-player record of which loot slots a player has
 * searched in regular (non-corpse) containers such as chests. Stored as world
 * SavedData so it survives reloads.
 *
 * Key: container BlockPos (encoded long) → (player UUID → set of searched slot indices)
 */
public class ContainerSearchData extends SavedData {

    private static final String NAME = "tarkov_container_search";

    private final Map<Long, Map<UUID, Set<Integer>>> data = new HashMap<>();

    public ContainerSearchData() {}

    public static ContainerSearchData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                ContainerSearchData::load, ContainerSearchData::new, NAME);
    }

    public Set<Integer> getSearched(BlockPos pos, UUID player) {
        Map<UUID, Set<Integer>> byPlayer = data.get(pos.asLong());
        if (byPlayer == null) return java.util.Collections.emptySet();
        return byPlayer.getOrDefault(player, java.util.Collections.emptySet());
    }

    public void markSearched(BlockPos pos, UUID player, int lootIdx) {
        data.computeIfAbsent(pos.asLong(), k -> new HashMap<>())
            .computeIfAbsent(player, k -> new HashSet<>())
            .add(lootIdx);
        setDirty();
    }

    /** Clears all searched state for a container (e.g. when its contents change). */
    public void clear(BlockPos pos) {
        if (data.remove(pos.asLong()) != null) setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag containers = new ListTag();
        data.forEach((posLong, byPlayer) -> {
            CompoundTag cTag = new CompoundTag();
            cTag.putLong("Pos", posLong);
            CompoundTag players = new CompoundTag();
            byPlayer.forEach((uuid, slots) -> {
                int[] arr = slots.stream().mapToInt(Integer::intValue).toArray();
                players.putIntArray(uuid.toString(), arr);
            });
            cTag.put("Players", players);
            containers.add(cTag);
        });
        tag.put("Containers", containers);
        return tag;
    }

    public static ContainerSearchData load(CompoundTag tag) {
        ContainerSearchData d = new ContainerSearchData();
        ListTag containers = tag.getList("Containers", Tag.TAG_COMPOUND);
        for (int i = 0; i < containers.size(); i++) {
            CompoundTag cTag = containers.getCompound(i);
            long posLong = cTag.getLong("Pos");
            CompoundTag players = cTag.getCompound("Players");
            Map<UUID, Set<Integer>> byPlayer = new HashMap<>();
            for (String key : players.getAllKeys()) {
                try {
                    UUID uuid = UUID.fromString(key);
                    Set<Integer> slots = new HashSet<>();
                    for (int s : players.getIntArray(key)) slots.add(s);
                    byPlayer.put(uuid, slots);
                } catch (IllegalArgumentException ignored) {}
            }
            d.data.put(posLong, byPlayer);
        }
        return d;
    }
}
