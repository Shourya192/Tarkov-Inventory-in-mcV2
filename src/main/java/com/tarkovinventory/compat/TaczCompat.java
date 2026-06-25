package com.tarkovinventory.compat;

import com.tarkovinventory.inventory.GridItemSizes;
import com.tarkovinventory.inventory.GridSize;
import net.minecraft.world.item.Item;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Soft compatibility with Timeless and Classics Zero (TACZ).
 *
 * Registers Tarkov-style grid sizes for TACZ firearm categories.
 * All access is guarded by {@link #isLoaded()}.
 */
public final class TaczCompat {

    private TaczCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("tacz");
    }

    /**
     * Call once during FMLCommonSetupEvent to register TACZ item sizes.
     * Safe to call even when TACZ is absent — isLoaded() guard prevents any work.
     */
    public static void registerSizes() {
        if (!isLoaded()) return;
        try {
            registerTaczSizes();
        } catch (Throwable ignored) {
            // Class not found or API change — degrade gracefully
        }
    }

    private static void registerTaczSizes() {
        // Walk the registry and assign sizes based on item class hierarchy or resource path
        for (Item item : ForgeRegistries.ITEMS) {
            String regName = ForgeRegistries.ITEMS.getKey(item) != null
                    ? ForgeRegistries.ITEMS.getKey(item).getPath()
                    : "";

            if (!ForgeRegistries.ITEMS.getKey(item).getNamespace().equals("tacz")) continue;

            String cls = item.getClass().getSimpleName().toLowerCase();

            // Pistols: compact sidearms
            if (cls.contains("pistol") || regName.contains("pistol")
                    || regName.contains("p226") || regName.contains("glock")
                    || regName.contains("m9") || regName.contains("deagle")
                    || regName.contains("tt") || regName.contains("five_seven")) {
                GridItemSizes.register(item, new GridSize(1, 2));

            // SMGs
            } else if (cls.contains("smg") || regName.contains("smg")
                    || regName.contains("mp5") || regName.contains("mp7")
                    || regName.contains("p90") || regName.contains("mac10")
                    || regName.contains("vector") || regName.contains("ump")) {
                GridItemSizes.register(item, new GridSize(2, 4));

            // Shotguns
            } else if (cls.contains("shotgun") || regName.contains("shotgun")
                    || regName.contains("saiga") || regName.contains("m870")
                    || regName.contains("spas")) {
                GridItemSizes.register(item, new GridSize(2, 5));

            // Sniper / DMR rifles (long)
            } else if (cls.contains("sniper") || regName.contains("sniper")
                    || regName.contains("mosin") || regName.contains("sks")
                    || regName.contains("dragunov") || regName.contains("svd")
                    || regName.contains("m24") || regName.contains("awp")
                    || regName.contains("l96")) {
                GridItemSizes.register(item, new GridSize(2, 8));

            // Light machine guns / heavy
            } else if (cls.contains("lmg") || regName.contains("lmg")
                    || regName.contains("pkm") || regName.contains("m249")
                    || regName.contains("rpk")) {
                GridItemSizes.register(item, new GridSize(3, 6));

            // Default: assault rifle / carbine
            } else if (cls.contains("gun") || cls.contains("rifle") || cls.contains("firearm")
                    || regName.contains("ak") || regName.contains("m4")
                    || regName.contains("hk") || regName.contains("aug")
                    || regName.contains("scar") || regName.contains("fal")) {
                GridItemSizes.register(item, new GridSize(2, 6));

            // Magazines
            } else if (cls.contains("magazine") || regName.contains("magazine")
                    || regName.contains("mag")) {
                GridItemSizes.register(item, new GridSize(1, 2));

            // Ammo boxes
            } else if (cls.contains("ammo") || regName.contains("ammo")
                    || regName.contains("bullet")) {
                GridItemSizes.register(item, new GridSize(1, 1));

            // Attachments (scopes, muzzles)
            } else if (cls.contains("attachment") || regName.contains("scope")
                    || regName.contains("suppressor") || regName.contains("muzzle")
                    || regName.contains("grip")) {
                GridItemSizes.register(item, new GridSize(1, 1));
            }
        }
    }
}
