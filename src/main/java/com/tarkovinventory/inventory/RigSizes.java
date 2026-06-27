package com.tarkovinventory.inventory;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry mapping rig item IDs to their custom grid dimensions (cols × rows).
 * This allows rigs to have independent storage from their original mod inventory.
 *
 * Unknown rigs default to 3×3 (9 slots).
 */
public final class RigSizes {

    private RigSizes() {}

    /** Default size for any rig not in the registry. */
    public static final int DEFAULT_COLS = 3;
    public static final int DEFAULT_ROWS = 3;

    /** id → [cols, rows] */
    private static final Map<String, int[]> REGISTRY = new HashMap<>();

    static {
        // ── Modern Mayhem rigs (curios "body") ─────────────────────────
        register("mm:tan_bandoleer",              3, 2);   //  6 slots
        register("mm:green_recon_rig",            4, 4);   // 16 slots
        register("mm:hexagon_rig",                3, 3);   //  9 slots
        register("mm:black_plate_carrier",        3, 3);   //  9 slots
        register("mm:tan_plate_carrier",          3, 3);   //  9 slots
        register("mm:black_plate_carrier_ammo",   4, 3);   // 12 slots
        register("mm:tan_plate_carrier_ammo",     4, 3);   // 12 slots
        register("mm:black_plate_carrier_pouches",3, 3);   //  9 slots
        register("mm:tan_plate_carrier_pouches",  3, 3);   //  9 slots
    }

    /**
     * Register a custom rig size. Call this from your mod's setup if you
     * want to add new entries at runtime without editing this file.
     */
    public static void register(String itemId, int cols, int rows) {
        cols = Math.min(cols, GridInventory.MAX_COLS);
        rows = Math.min(rows, GridInventory.MAX_ROWS);
        REGISTRY.put(itemId, new int[]{cols, rows});
    }

    /** Returns the grid cols for the given rig, or DEFAULT_COLS. */
    public static int getCols(ItemStack stack) {
        return getSize(stack)[0];
    }

    /** Returns the grid rows for the given rig, or DEFAULT_ROWS. */
    public static int getRows(ItemStack stack) {
        return getSize(stack)[1];
    }

    /**
     * Returns true if this item has an explicit entry in the registry.
     * A false result means it will use the default 3×3 size.
     */
    public static boolean isRegistered(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && REGISTRY.containsKey(key.toString());
    }

    /**
     * Returns the item's registry ID string, or "unknown" if not found.
     * Useful for debugging.
     */
    public static String getItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "(empty)";
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null ? key.toString() : "unknown";
    }

    private static int[] getSize(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new int[]{DEFAULT_COLS, DEFAULT_ROWS};
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return new int[]{DEFAULT_COLS, DEFAULT_ROWS};
        return REGISTRY.getOrDefault(key.toString(), new int[]{DEFAULT_COLS, DEFAULT_ROWS});
    }
}
