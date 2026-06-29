package com.tarkovinventory.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-wide, admin-assigned slot type for items, set via the admin tagging UI.
 * Maps an item registry ID (e.g. "minecraft:leather_chestplate") to a slot type
 * name (RIG, BACKPACK, FACE, EAR, HEAD, ARMOR, PANTS, BOOTS, KNEE, or NONE).
 *
 * Persisted to config/tarkov_item_types.json so it survives restarts. The slot
 * validators ({@link com.tarkovinventory.inventory.EquipmentSlotTypeValidator})
 * consult this first, so admin assignments override the built-in heuristics.
 */
public final class ItemTypeConfig {

    private ItemTypeConfig() {}

    /** itemId → slot type name (uppercase). "NONE" means explicitly unassigned. */
    private static final Map<String, String> TYPES = new LinkedHashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path configPath = null;

    public static void initConfig(Path configDir) {
        configPath = configDir.resolve("tarkov_item_types.json");
        loadConfig();
    }

    /** Returns the admin-assigned type name for an item, or null if none. */
    public static String getType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) return null;
        String t = TYPES.get(key.toString());
        return (t == null || t.equals("NONE")) ? null : t;
    }

    public static String getType(String itemId) {
        String t = TYPES.get(itemId);
        return (t == null || t.equals("NONE")) ? null : t;
    }

    /** Assigns (or clears, if typeName is null/"NONE") a slot type for an item. */
    public static void setType(String itemId, String typeName) {
        if (typeName == null || typeName.equalsIgnoreCase("NONE")) {
            TYPES.remove(itemId);
        } else {
            TYPES.put(itemId, typeName.toUpperCase());
        }
        saveConfig();
    }

    public static Map<String, String> getAll() {
        return new LinkedHashMap<>(TYPES);
    }

    @SuppressWarnings("unchecked")
    private static void loadConfig() {
        if (configPath == null || !Files.exists(configPath)) return;
        try (Reader r = Files.newBufferedReader(configPath)) {
            Map<String, String> raw = GSON.fromJson(r,
                    new TypeToken<Map<String, String>>(){}.getType());
            if (raw != null) {
                TYPES.clear();
                raw.forEach((k, v) -> { if (v != null) TYPES.put(k, v.toUpperCase()); });
            }
        } catch (IOException | RuntimeException ex) {
            // Corrupt/unreadable — keep whatever is in memory
        }
    }

    private static void saveConfig() {
        if (configPath == null) return;
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer w = Files.newBufferedWriter(configPath)) {
                GSON.toJson(new LinkedHashMap<>(TYPES), w);
            }
        } catch (IOException ex) {
            // Non-fatal
        }
    }
}
