package com.tarkovinventory.inventory;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry mapping rig item IDs to their custom grid dimensions (cols × rows).
 * This allows rigs to have independent storage from their original mod inventory.
 *
 * Built-in defaults are registered in the static block. Server operators can
 * override any rig TYPE's size with /ti setrigsize, which persists to
 * config/tarkov_rig_sizes.json and reloads automatically on server start.
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
    /** prefix → [cols, rows] — matched when no exact ID entry exists */
    private static final Map<String, int[]> PREFIX_REGISTRY = new HashMap<>();
    /** Operator overrides (from command / config) — persisted, take top priority. */
    private static final Map<String, int[]> OVERRIDES = new LinkedHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath = null;

    static {
        // ── Modern Mayhem rigs (curios "body") ─────────────────────────
        register("mm:tan_bandoleer",              3, 2);
        register("mm:green_recon_rig",            4, 4);
        register("mm:hexagon_rig",                3, 3);
        register("mm:black_plate_carrier",        3, 3);
        register("mm:tan_plate_carrier",          3, 3);
        register("mm:black_plate_carrier_ammo",   4, 3);
        register("mm:tan_plate_carrier_ammo",     4, 3);
        register("mm:black_plate_carrier_pouches",3, 3);
        register("mm:tan_plate_carrier_pouches",  3, 3);

        // ── Miklos Military Armor — all mm_armor: items used as rigs ────
        // No Curios integration; placed in the Tarkov RIG slot directly.
        // Default 3×3. Override specific types by exact ID or use /ti setrigsize.

        // Belt-only items: wide but shallow (2 rows)
        registerPrefix("mm_armor:belt",        4, 2);
        registerPrefix("mm_armor:medicbelt",   4, 2);

        // IBA / IOTV (Interceptor Body Armor): large plate carriers
        registerPrefix("mm_armor:ibavest",     4, 4);
        registerPrefix("mm_armor:iotv",        4, 4);
        registerPrefix("mm_armor:chestplateacu",4,4);
        registerPrefix("mm_armor:dcuchestplate",4,4);
        registerPrefix("mm_armor:desertcamochestplate",4,4);
        registerPrefix("mm_armor:marpatchestplate",4,4);
        registerPrefix("mm_armor:multicamchestplate",4,4);
        registerPrefix("mm_armor:multitarnchestplate",4,4);

        // Plate carriers (modern)
        registerPrefix("mm_armor:platecarrier",  4, 3);

        // 6B-series Russian vests — sized after real Tarkov equivalents
        register("mm_armor:vest_6b_2_chestplate",     3, 2);
        register("mm_armor:vest_6b_2olive_chestplate",3, 2);
        register("mm_armor:vest_6b_3_chestplate",     3, 2);
        register("mm_armor:vest_6b_12_chestplate",    4, 2);
        register("mm_armor:vest_6b_13emr_chestplate", 4, 2);
        register("mm_armor:vest_6b_13tan_chestplate", 4, 2);
        register("mm_armor:vest_6b_23_chestplate",    4, 3);
        register("mm_armor:vest_6b_23upgraded_chestplate",5,3);
        register("mm_armor:vest_6b_2_chestplate",     3, 2);
        register("mm_armor:vest_6b_4_chestplate",     4, 3);
        register("mm_armor:vest_6b_4green_chestplate",4, 3);
        register("mm_armor:vest_6b_5_chestplate",     4, 3);
        register("mm_armor:vest_6b_5green_chestplate",4, 3);
        register("mm_armor:vest_6b_45_chestplate",    5, 3);
        register("mm_armor:vest_6b_45classicupgraded_chestplate",5,4);
        register("mm_armor:vest_6b_45upgraded_chestplate",5,3);
        register("mm_armor:vest_6b_46_chestplate",    5, 3);
        register("mm_armor:vest_6b_46upgraded_chestplate",5,3);
        register("mm_armor:vest_6sh_117_chestplate",  3, 2);
        register("mm_armor:vest_6sh_117desert_chestplate",3,2);
        register("mm_armor:vest_korsarm_3_chestplate",4, 3);
        register("mm_armor:vestdefender_2_chestplate",4, 3);
        register("mm_armor:vestdefender_2black_chestplate",4,3);
        register("mm_armor:vestdesert_6b_45_chestplate",5,3);
        register("mm_armor:vestdesert_6b_45classicupgraded_chestplate",5,4);
        register("mm_armor:vestdesert_6b_45upgraded_chestplate",5,3);
        register("mm_armor:vestlifchik_chestplate",   3, 2);
        register("mm_armor:vestplatecarriermm_14_chestplate",4,3);
        register("mm_armor:vestplatecarriermm_14upgraded_chestplate",5,3);
        register("mm_armor:vestubv_3_chestplate",     4, 2);
        register("mm_armor:vestun_chestplate",        3, 2);
        register("mm_armor:vestusgi_chestplate",      4, 3);

        // Catch-all: any remaining mm_armor: chestplate defaults to 3×3
        registerPrefix("mm_armor:", 3, 3);
    }

    public static void register(String itemId, int cols, int rows) {
        cols = Math.min(cols, GridInventory.MAX_COLS);
        rows = Math.min(rows, GridInventory.MAX_ROWS);
        REGISTRY.put(itemId, new int[]{cols, rows});
    }

    /**
     * Permanently override a rig TYPE's size (set via /ti setrigsize).
     * Saved to config so it survives world reloads and server restarts.
     */
    public static void registerOverride(String itemId, int cols, int rows) {
        cols = Math.max(1, Math.min(cols, GridInventory.MAX_COLS));
        rows = Math.max(1, Math.min(rows, GridInventory.MAX_ROWS));
        OVERRIDES.put(itemId, new int[]{cols, rows});
        saveConfig();
    }

    /** Sets the config file location (called once on server start) and loads it. */
    public static void initConfig(Path configDir) {
        configPath = configDir.resolve("tarkov_rig_sizes.json");
        loadConfig();
    }

    @SuppressWarnings("unchecked")
    private static void loadConfig() {
        if (configPath == null || !Files.exists(configPath)) return;
        try (Reader r = Files.newBufferedReader(configPath)) {
            Map<String, java.util.List<Double>> raw = GSON.fromJson(r,
                    new TypeToken<Map<String, java.util.List<Double>>>(){}.getType());
            if (raw != null) {
                OVERRIDES.clear();
                for (var e : raw.entrySet()) {
                    if (e.getValue() != null && e.getValue().size() >= 2) {
                        int c = e.getValue().get(0).intValue();
                        int rw = e.getValue().get(1).intValue();
                        OVERRIDES.put(e.getKey(), new int[]{
                                Math.max(1, Math.min(c, GridInventory.MAX_COLS)),
                                Math.max(1, Math.min(rw, GridInventory.MAX_ROWS))});
                    }
                }
            }
        } catch (IOException | RuntimeException ex) {
            // Corrupt or unreadable config — ignore, keep built-in defaults
        }
    }

    private static void saveConfig() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            Map<String, int[]> out = new LinkedHashMap<>(OVERRIDES);
            try (Writer w = Files.newBufferedWriter(configPath)) {
                GSON.toJson(out, w);
            }
        } catch (IOException ex) {
            // Couldn't write — non-fatal, override still active in memory this session
        }
    }

    /**
     * Register a size for all items whose ID starts with the given prefix.
     * Exact matches always take priority over prefix matches.
     */
    public static void registerPrefix(String prefix, int cols, int rows) {
        cols = Math.min(cols, GridInventory.MAX_COLS);
        rows = Math.min(rows, GridInventory.MAX_ROWS);
        PREFIX_REGISTRY.put(prefix, new int[]{cols, rows});
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
        if (key == null) return false;
        String id = key.toString();
        if (OVERRIDES.containsKey(id)) return true;
        if (REGISTRY.containsKey(id)) return true;
        return PREFIX_REGISTRY.keySet().stream().anyMatch(id::startsWith);
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
        String id = key.toString();
        // Operator override (command/config) takes top priority
        if (OVERRIDES.containsKey(id)) return OVERRIDES.get(id);
        // Exact built-in match
        if (REGISTRY.containsKey(id)) return REGISTRY.get(id);
        // Prefix match (longest matching prefix wins)
        String best = null;
        for (String prefix : PREFIX_REGISTRY.keySet()) {
            if (id.startsWith(prefix) && (best == null || prefix.length() > best.length())) {
                best = prefix;
            }
        }
        if (best != null) return PREFIX_REGISTRY.get(best);
        return new int[]{DEFAULT_COLS, DEFAULT_ROWS};
    }
}
