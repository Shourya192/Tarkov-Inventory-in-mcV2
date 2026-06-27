package com.tarkovinventory.inventory;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry mapping backpack / rig item IDs to their grid dimensions (cols × rows).
 *
 * Unknown backpacks default to 6×6 so they still work without being registered.
 * Add entries here whenever you want a specific backpack to have a precise size.
 */
public final class BackpackSizes {

    private BackpackSizes() {}

    /** Default size for any backpack not in the registry. */
    public static final int DEFAULT_COLS = 6;
    public static final int DEFAULT_ROWS = 6;

    /** id → [cols, rows] */
    private static final Map<String, int[]> REGISTRY = new HashMap<>();

    static {
        // ── Our own backpack ──────────────────────────────────────────────
        register("tarkovinventory:tactical_backpack", 8, 8);

        // ── Sophisticated Backpacks ───────────────────────────────────────
        register("sophisticatedbackpacks:backpack",           4, 9);
        register("sophisticatedbackpacks:iron_backpack",      5, 9);
        register("sophisticatedbackpacks:gold_backpack",      6, 9);
        register("sophisticatedbackpacks:diamond_backpack",   7, 9);
        register("sophisticatedbackpacks:netherite_backpack", 8, 9);

        // ── Traveler's Backpack ───────────────────────────────────────────
        register("travelersbackpack:standard_backpack", 6, 4);
        register("travelersbackpack:leather_backpack",  5, 4);
        register("travelersbackpack:wither_backpack",   9, 6);

        // ── Iron Backpacks ────────────────────────────────────────────────
        register("ironbackpacks:basic_backpack",   3, 5);
        register("ironbackpacks:iron_backpack",    4, 6);
        register("ironbackpacks:gold_backpack",    5, 7);
        register("ironbackpacks:diamond_backpack", 6, 8);
        register("ironbackpacks:crystal_backpack", 7, 9);

        // ── Quark ─────────────────────────────────────────────────────────
        register("quark:backpack", 4, 4);

        // ── Modern Mayhem (mod ID: mm) ─ backpacks ────────────────────────
        // T1 ≈ 3×3 = 9 slots (config-driven; defaults are Tarkov-inspired)
        register("mm:black_backpack_t1", 3, 3);
        register("mm:green_backpack_t1", 3, 3);
        register("mm:tan_backpack_t1",   3, 3);
        // T2 ≈ 4×4 = 16 slots
        register("mm:black_backpack_t2", 4, 4);
        register("mm:green_backpack_t2", 4, 4);
        register("mm:tan_backpack_t2",   4, 4);
        // T3 ≈ 5×5 = 25 slots
        register("mm:black_backpack_t3", 5, 5);
        register("mm:green_backpack_t3", 5, 5);
        register("mm:tan_backpack_t3",   5, 5);
        // Duffel bag ≈ 6×6 = 36 slots
        register("mm:duffel_bag", 6, 6);

        // ── Modern Mayhem (mod ID: mm) ─ rigs (curios "body") ─────────────
        register("mm:tan_bandoleer",              3, 2);   //  6 slots
        register("mm:green_recon_rig",            4, 4);   // 16 slots
        register("mm:hexagon_rig",                3, 3);   //  9 slots
        register("mm:black_plate_carrier",        3, 3);   //  9 slots
        register("mm:tan_plate_carrier",          3, 3);   //  9 slots
        register("mm:black_plate_carrier_ammo",   4, 3);   // 12 slots
        register("mm:tan_plate_carrier_ammo",     4, 3);   // 12 slots
        register("mm:black_plate_carrier_pouches",3, 3);   //  9 slots
        register("mm:tan_plate_carrier_pouches",  3, 3);   //  9 slots

        // ── Survivor's Arsenal (mod ID: survivorsarsenal) ─ backpacks ─────
        // Small backpacks — 18 slots (2 rows × 9)
        register("survivorsarsenal:backpack_small_black", 9, 2);
        register("survivorsarsenal:backpack_small_green", 9, 2);
        register("survivorsarsenal:backpack_small_pink",  9, 2);
        register("survivorsarsenal:backpack_small_blue",  9, 2);
        // Hiking backpacks — 36 slots (4 rows × 9)
        register("survivorsarsenal:hiking_backpack_black",      9, 4);
        register("survivorsarsenal:hiking_backpack_red",        9, 4);
        register("survivorsarsenal:hiking_backpack_blue",       9, 4);
        register("survivorsarsenal:hiking_backpack_light_brown",9, 4);
        // Military backpacks — 54 slots (6 rows × 9)
        register("survivorsarsenal:military_backpack_black",  9, 6);
        register("survivorsarsenal:military_backpack_green",  9, 6);
        register("survivorsarsenal:military_backpack_desert", 9, 6);
        // Leather backpack — 36 slots (same class as hiking)
        register("survivorsarsenal:leather_backpack", 9, 4);

        // ── Miklos Military Armor (mm_armor) ──────────────────────────────
        // Pure armor items — no inventory. No registration needed here;
        // they will never appear in a backpack / rig slot.
    }

    /**
     * Register a custom backpack size. Call this from your mod's setup if you
     * want to add new entries at runtime without editing this file.
     */
    public static void register(String itemId, int cols, int rows) {
        cols = Math.min(cols, GridInventory.MAX_COLS);
        rows = Math.min(rows, GridInventory.MAX_ROWS);
        REGISTRY.put(itemId, new int[]{cols, rows});
    }

    /** Returns the grid cols for the given backpack, or DEFAULT_COLS. */
    public static int getCols(ItemStack stack) {
        return getSize(stack)[0];
    }

    /** Returns the grid rows for the given backpack, or DEFAULT_ROWS. */
    public static int getRows(ItemStack stack) {
        return getSize(stack)[1];
    }

    /**
     * Returns true if this item has an explicit entry in the registry.
     * A false result means it will use the default 6×6 size.
     */
    public static boolean isRegistered(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key != null && REGISTRY.containsKey(key.toString());
    }

    /**
     * Returns the item's registry ID string, or "unknown" if not found.
     * Useful for debugging / the gridinfo command.
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
