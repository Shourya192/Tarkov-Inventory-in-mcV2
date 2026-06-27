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

    public TarkovCorpseBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TARKOV_CORPSE.get(), pos, state);
    }

    // ── Accessors ────────────────────────────────────────────────────

    public String                 getOwnerName()     { return ownerName; }
    public Map<String, ItemStack> getSlottedItems()  { return Collections.unmodifiableMap(slottedItems); }
    public List<ItemStack>        getInventoryItems() { return Collections.unmodifiableList(inventoryItems); }

    public boolean isEmpty() { return slottedItems.isEmpty() && inventoryItems.isEmpty(); }

    public void setOwnerName(String name)  { ownerName = name; setChanged(); }

    public void setSlottedItems(Map<String, ItemStack> items) {
        slottedItems = new LinkedHashMap<>();
        items.forEach((k, v) -> { if (!v.isEmpty()) slottedItems.put(k, v.copy()); });
        setChanged();
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
        // Legacy: old flat "Items" tag (pre-redesign)
        if (slottedItems.isEmpty() && inventoryItems.isEmpty() && tag.contains("Items", Tag.TAG_LIST)) {
            ListTag legacy = tag.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < legacy.size(); i++) {
                ItemStack s = ItemStack.of(legacy.getCompound(i));
                if (!s.isEmpty()) inventoryItems.add(s);
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
