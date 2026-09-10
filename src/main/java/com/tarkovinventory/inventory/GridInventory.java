package com.tarkovinventory.inventory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A grid-based inventory that tracks the position of each ItemStack on a 2-D grid.
 *
 * The backing store is always MAX_COLS × MAX_ROWS = 144 slots.
 * The active region (activeCols × activeRows) is set at runtime based on the
 * equipped backpack — items outside the active region are preserved in NBT but
 * are not accessible until the region grows back.
 *
 * Slot index = gridY * MAX_COLS + gridX  (always based on MAX_COLS so slot
 * positions remain stable when active dimensions change).
 */
public class GridInventory extends SimpleContainer {

    public static final int MAX_COLS   = 12;
    public static final int MAX_ROWS   = 12;
    public static final int MAX_CELLS  = MAX_COLS * MAX_ROWS; // 144

    // Kept for backward compat — code that references GridInventory.COLS etc.
    /** @deprecated use getActiveCols() */
    @Deprecated public static final int COLS        = 8;
    /** @deprecated use getActiveRows() */
    @Deprecated public static final int ROWS        = 8;
    /** @deprecated use MAX_CELLS */
    @Deprecated public static final int TOTAL_CELLS = MAX_CELLS;

    // ── Active region ─────────────────────────────────────────────────
    private int activeCols = 8;
    private int activeRows = 8;

    // ── Per-slot metadata ─────────────────────────────────────────────
    /** anchor column for each slot (indexed by slot = gridY*MAX_COLS+gridX) */
    private final int[]      slotX    = new int[MAX_CELLS];
    /** anchor row for each slot */
    private final int[]      slotY    = new int[MAX_CELLS];
    /** size for each slot */
    private final GridSize[] slotSize = new GridSize[MAX_CELLS];
    /** fast O(1) cell-occupied lookup */
    private final boolean[]  occupied = new boolean[MAX_CELLS];

    public GridInventory() {
        super(MAX_CELLS);
        for (int i = 0; i < MAX_CELLS; i++) slotSize[i] = GridSize.ONE_BY_ONE;
    }

    // ── Active dimensions ─────────────────────────────────────────────

    public int getActiveCols() { return activeCols; }
    public int getActiveRows() { return activeRows; }

    /**
     * Change the active region. Items outside the new region remain in the
     * backing store but will not be reachable until the region expands again.
     */
    public void setActiveDimensions(int cols, int rows) {
        activeCols = Math.max(1, Math.min(cols, MAX_COLS));
        activeRows = Math.max(1, Math.min(rows, MAX_ROWS));
    }

    // ── Placement helpers ─────────────────────────────────────────────

    /** True if the given region is fully within the active area and unoccupied. */
    public boolean canPlace(int gridX, int gridY, GridSize size) {
        if (gridX < 0 || gridY < 0
                || gridX + size.width()  > activeCols
                || gridY + size.height() > activeRows) return false;
        for (int dy = 0; dy < size.height(); dy++)
            for (int dx = 0; dx < size.width(); dx++)
                if (occupied[(gridY + dy) * MAX_COLS + (gridX + dx)]) return false;
        return true;
    }

    /**
     * Places the item at (gridX, gridY). Returns the slot index used, or -1.
     */
    public int placeItem(ItemStack stack, int gridX, int gridY, GridSize size) {
        if (!canPlace(gridX, gridY, size)) return -1;
        int slotIdx = gridY * MAX_COLS + gridX;
        setItem(slotIdx, stack);
        slotX[slotIdx]    = gridX;
        slotY[slotIdx]    = gridY;
        slotSize[slotIdx] = size;
        markOccupied(gridX, gridY, size, true);
        return slotIdx;
    }

    /** Removes the item whose anchor is at slotIdx and frees its cells. */
    public ItemStack removeItem(int slotIdx) {
        ItemStack stack = getItem(slotIdx);
        if (!stack.isEmpty()) {
            markOccupied(slotX[slotIdx], slotY[slotIdx], slotSize[slotIdx], false);
            slotSize[slotIdx] = GridSize.ONE_BY_ONE;
            setItem(slotIdx, ItemStack.EMPTY);
        }
        return stack;
    }

    /**
     * Auto-places by scanning the active region for the first fitting spot.
     * Returns the slot index used, or -1 if no space.
     */
    public int autoPlace(ItemStack stack) {
        GridSize size = GridItemSizes.getSize(stack.getItem());
        for (int row = 0; row <= activeRows - size.height(); row++)
            for (int col = 0; col <= activeCols - size.width(); col++) {
                int idx = placeItem(stack, col, row, size);
                if (idx >= 0) return idx;
            }
        return -1;
    }

    // ── Accessors ─────────────────────────────────────────────────────

    public int     getSlotX(int slotIdx)    { return slotX[slotIdx]; }
    public int     getSlotY(int slotIdx)    { return slotY[slotIdx]; }
    public GridSize getSlotSize(int slotIdx) { return slotSize[slotIdx]; }
    public boolean isCellOccupied(int col, int row) {
        return col >= 0 && col < MAX_COLS && row >= 0 && row < MAX_ROWS
               && occupied[row * MAX_COLS + col];
    }

    /**
     * Returns the anchor slot for the cell at (col, row), or -1 if empty.
     * Only searches within the active region.
     */
    public int getAnchorSlot(int col, int row) {
        if (col < 0 || col >= activeCols || row < 0 || row >= activeRows) return -1;
        int cell = row * MAX_COLS + col;
        if (!occupied[cell]) return -1;
        for (int i = 0; i < MAX_CELLS; i++) {
            if (getItem(i).isEmpty()) continue;
            int ax = slotX[i], ay = slotY[i];
            GridSize s = slotSize[i];
            if (col >= ax && col < ax + s.width() && row >= ay && row < ay + s.height())
                return i;
        }
        return -1;
    }

    // ── NBT persistence ───────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SavedMaxCols", activeCols);   // so load() knows the stride used
        tag.putInt("SavedMaxRows", activeRows);
        ListTag list = new ListTag();
        for (int i = 0; i < MAX_CELLS; i++) {
            ItemStack stack = getItem(i);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putInt("SlotIndex", i);
            entry.putInt("GridX",     slotX[i]);
            entry.putInt("GridY",     slotY[i]);
            entry.putInt("SizeW",     slotSize[i].width());
            entry.putInt("SizeH",     slotSize[i].height());
            entry.put("Item", stack.save(new CompoundTag()));
            list.add(entry);
        }
        tag.put("Items", list);
        return tag;
    }

    public void load(CompoundTag tag) {
        // Clear state
        for (int i = 0; i < MAX_CELLS; i++) {
            setItem(i, ItemStack.EMPTY);
            occupied[i]  = false;
            slotSize[i]  = GridSize.ONE_BY_ONE;
        }

        // The stride used when the data was saved (old saves used 8, new ones use MAX_COLS=12)
        int savedCols = tag.contains("SavedMaxCols") ? tag.getInt("SavedMaxCols") : 8;
        int savedRows = tag.contains("SavedMaxRows") ? tag.getInt("SavedMaxRows") : savedCols;

        setActiveDimensions(savedCols, savedRows);

        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int gx = entry.getInt("GridX");
            int gy = entry.getInt("GridY");
            int sw = entry.getInt("SizeW");
            int sh = entry.getInt("SizeH");
            ItemStack stack = ItemStack.of(entry.getCompound("Item"));
            if (stack.isEmpty() || gx < 0 || gy < 0 || gx >= MAX_COLS || gy >= MAX_ROWS) continue;

            int newIdx = gy * MAX_COLS + gx;
            GridSize gs = new GridSize(sw, sh);
            setItem(newIdx, stack);
            slotX[newIdx]    = gx;
            slotY[newIdx]    = gy;
            slotSize[newIdx] = gs;
            markOccupied(gx, gy, gs, true);
        }
    }

    // ── Internal ──────────────────────────────────────────────────────

    private void markOccupied(int gx, int gy, GridSize size, boolean value) {
        for (int dy = 0; dy < size.height(); dy++)
            for (int dx = 0; dx < size.width(); dx++) {
                int cell = (gy + dy) * MAX_COLS + (gx + dx);
                if (cell >= 0 && cell < MAX_CELLS) occupied[cell] = value;
            }
    }
}
