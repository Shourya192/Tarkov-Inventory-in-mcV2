package com.tarkovinventory.block;

import com.tarkovinventory.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;

/**
 * Stores the inventory of a player who died, split into:
 *   slottedItems   — equipment/curios slots keyed by slot-id (e.g. "armor.head", "curios.back")
 *   inventoryItems — remaining main-inventory stacks (flat list)
 */
public class TarkovCorpseBlockEntity extends BlockEntity {

    private Map<String, ItemStack> slottedItems  = new LinkedHashMap<>();
    private List<ItemStack>        inventoryItems = new ArrayList<>();
    private String                 ownerName      = "Unknown";

    // ── Structured corpse data ───────────────────────────────────────
    /** Pockets: hotbar slots 2-8 (always 7, preserves empty positions). */
    private ItemStack[]     pockets      = newEmptyPockets();
    /** Flat contents of the rig, indexed by col + row * rigCols. */
    private List<ItemStack> rigContents  = new ArrayList<>();
    private int             rigCols = 3, rigRows = 3;
    /** Flat contents of the backpack grid, MAX_COLS-indexed. */
    private List<ItemStack> gridContents = new ArrayList<>();
    private int             gridCols = 6, gridRows = 6;

    /** Loot slot indices each player has searched, keyed by player UUID.
     *  Per-player (Tarkov-style): each player must search the corpse themselves. */
    private final java.util.Map<java.util.UUID, java.util.Set<Integer>> searchedSlots = new java.util.HashMap<>();

    public java.util.Set<Integer> getSearchedSlots(java.util.UUID player) {
        return searchedSlots.getOrDefault(player, java.util.Collections.emptySet());
    }
    public void markSlotSearched(java.util.UUID player, int lootIdx) {
        searchedSlots.computeIfAbsent(player, k -> new java.util.HashSet<>()).add(lootIdx);
        setChanged();
    }
    public boolean isSlotSearched(java.util.UUID player, int lootIdx) {
        java.util.Set<Integer> s = searchedSlots.get(player);
        return s != null && s.contains(lootIdx);
    }

    private static ItemStack[] newEmptyPockets() {
        ItemStack[] a = new ItemStack[7];
        java.util.Arrays.fill(a, ItemStack.EMPTY);
        return a;
    }

    public TarkovCorpseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TARKOV_CORPSE.get(), pos, state);
    }

    // ── Accessors ────────────────────────────────────────────────────

    public String                 getOwnerName()     { return ownerName; }
    public Map<String, ItemStack> getSlottedItems()  { return Collections.unmodifiableMap(slottedItems); }
    public List<ItemStack>        getInventoryItems() { return Collections.unmodifiableList(inventoryItems); }

    public boolean isEmpty() { return slottedItems.isEmpty() && inventoryItems.isEmpty()
            && allEmpty(pockets) && rigContents.isEmpty() && gridContents.isEmpty(); }

    // Structured getters
    public ItemStack[]     getPockets()      { return pockets; }
    public List<ItemStack> getRigContents()  { return Collections.unmodifiableList(rigContents); }
    public int             getRigCols()      { return rigCols; }
    public int             getRigRows()      { return rigRows; }
    public List<ItemStack> getGridContents() { return Collections.unmodifiableList(gridContents); }
    public int             getGridCols()     { return gridCols; }
    public int             getGridRows()     { return gridRows; }

    public void setSlottedItems(Map<String, ItemStack> items) {
        slottedItems = new LinkedHashMap<>(items);
        setChanged();
    }

    public void setOwnerName(String name) { ownerName = name; setChanged(); }

    public void setPockets(List<ItemStack> items) {
        pockets = newEmptyPockets();
        for (int i = 0; i < Math.min(items.size(), 7); i++)
            pockets[i] = items.get(i).isEmpty() ? ItemStack.EMPTY : items.get(i).copy();
        setChanged();
    }

    public void setPocketItem(int idx, ItemStack stack) {
        if (idx >= 0 && idx < 7) { pockets[idx] = stack.copy(); setChanged(); }
    }

    public void setRigContents(List<ItemStack> items, int cols, int rows) {
        rigContents = new ArrayList<>();
        items.forEach(s -> rigContents.add(s.isEmpty() ? ItemStack.EMPTY : s.copy()));
        rigCols = Math.max(1, cols); rigRows = Math.max(1, rows);
        setChanged();
    }

    public void setGridContents(List<ItemStack> items, int cols, int rows) {
        gridContents = new ArrayList<>();
        items.forEach(s -> gridContents.add(s.isEmpty() ? ItemStack.EMPTY : s.copy()));
        gridCols = Math.max(1, cols); gridRows = Math.max(1, rows);
        setChanged();
    }

    private static boolean allEmpty(ItemStack[] a) {
        for (ItemStack s : a) if (!s.isEmpty()) return false; return true;
    }

    public void setInventoryItems(List<ItemStack> stacks) {
        inventoryItems = new ArrayList<>();
        for (ItemStack s : stacks) if (!s.isEmpty()) inventoryItems.add(s.copy());
        setChanged();
    }

    // ── Item removal ──────────────────────────────────────────────────

    /** Remove and return item from a named equipment slot. */
    public ItemStack takeSlottedItem(String key) {
        ItemStack s = slottedItems.remove(key);
        if (s != null) { setChanged(); return s; }
        return ItemStack.EMPTY;
    }

    /** Remove and return item from main-inventory by index. */
    public ItemStack takeInventoryItem(int slot) {
        if (slot < 0 || slot >= inventoryItems.size()) return ItemStack.EMPTY;
        ItemStack s = inventoryItems.remove(slot);
        setChanged();
        return s;
    }

    /** Drain everything and return a flat list. */
    public List<ItemStack> takeAll() {
        List<ItemStack> all = new ArrayList<>(slottedItems.values());
        all.addAll(inventoryItems);
        slottedItems.clear();
        inventoryItems.clear();
        setChanged();
        return all;
    }

    // ── NBT ──────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag) {
        tag.putString("OwnerName", ownerName);
        CompoundTag st = new CompoundTag();
        slottedItems.forEach((k, v) -> { CompoundTag ct = new CompoundTag(); v.save(ct); st.put(k, ct); });
        tag.put("Slotted", st);
        ListTag inv = new ListTag();
        for (ItemStack s : inventoryItems) { CompoundTag ct = new CompoundTag(); s.save(ct); inv.add(ct); }
        tag.put("Inventory", inv);

        // Structured data
        ListTag pk = new ListTag();
        for (ItemStack s : pockets) { CompoundTag ct = new CompoundTag(); s.save(ct); pk.add(ct); }
        tag.put("Pockets", pk);
        tag.putInt("RigCols", rigCols); tag.putInt("RigRows", rigRows);
        ListTag rc = new ListTag();
        for (ItemStack s : rigContents) { CompoundTag ct = new CompoundTag(); s.save(ct); rc.add(ct); }
        tag.put("RigContents", rc);
        tag.putInt("GridCols", gridCols); tag.putInt("GridRows", gridRows);
        ListTag gc = new ListTag();
        for (ItemStack s : gridContents) { CompoundTag ct = new CompoundTag(); s.save(ct); gc.add(ct); }
        tag.put("GridContents", gc);
        // Persist per-player searched slots: a compound of UUID-string → int[]
        CompoundTag searchedTag = new CompoundTag();
        searchedSlots.forEach((uuid, set) -> {
            int[] arr = set.stream().mapToInt(Integer::intValue).toArray();
            searchedTag.putIntArray(uuid.toString(), arr);
        });
        tag.put("SearchedByPlayer", searchedTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ownerName = tag.getString("OwnerName");
        slottedItems = new LinkedHashMap<>();
        if (tag.contains("Slotted", Tag.TAG_COMPOUND)) {
            CompoundTag st = tag.getCompound("Slotted");
            for (String k : st.getAllKeys()) {
                ItemStack s = ItemStack.of(st.getCompound(k));
                if (!s.isEmpty()) slottedItems.put(k, s);
            }
        }
        inventoryItems = new ArrayList<>();
        ListTag inv = tag.getList("Inventory", Tag.TAG_COMPOUND);
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = ItemStack.of(inv.getCompound(i));
            if (!s.isEmpty()) inventoryItems.add(s);
        }
        // Legacy
        if (slottedItems.isEmpty() && inventoryItems.isEmpty() && tag.contains("Items", Tag.TAG_LIST)) {
            ListTag legacy = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < legacy.size(); i++) {
                ItemStack s = ItemStack.of(legacy.getCompound(i));
                if (!s.isEmpty()) inventoryItems.add(s);
            }
        }
        // Structured data
        pockets = newEmptyPockets();
        ListTag pk = tag.getList("Pockets", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(pk.size(), 7); i++) pockets[i] = ItemStack.of(pk.getCompound(i));
        rigCols = tag.contains("RigCols") ? tag.getInt("RigCols") : 3;
        rigRows = tag.contains("RigRows") ? tag.getInt("RigRows") : 3;
        rigContents = new ArrayList<>();
        ListTag rc = tag.getList("RigContents", Tag.TAG_COMPOUND);
        for (int i = 0; i < rc.size(); i++) rigContents.add(ItemStack.of(rc.getCompound(i)));
        gridCols = tag.contains("GridCols") ? tag.getInt("GridCols") : 6;
        gridRows = tag.contains("GridRows") ? tag.getInt("GridRows") : 6;
        gridContents = new ArrayList<>();
        ListTag gc = tag.getList("GridContents", Tag.TAG_COMPOUND);
        for (int i = 0; i < gc.size(); i++) gridContents.add(ItemStack.of(gc.getCompound(i)));
        searchedSlots.clear();
        if (tag.contains("SearchedByPlayer", Tag.TAG_COMPOUND)) {
            CompoundTag searchedTag = tag.getCompound("SearchedByPlayer");
            for (String key : searchedTag.getAllKeys()) {
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(key);
                    java.util.Set<Integer> set = new java.util.HashSet<>();
                    for (int s : searchedTag.getIntArray(key)) set.add(s);
                    searchedSlots.put(uuid, set);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    // ── Client sync ──────────────────────────────────────────────────

    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag(); saveAdditional(tag); return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) load(pkt.getTag());
    }
}
