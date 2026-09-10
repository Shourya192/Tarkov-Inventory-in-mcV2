package com.tarkovinventory.inventory;

import net.minecraft.world.item.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps item categories/types to their Tarkov-style grid sizes.
 * Items not explicitly registered default to 1x1.
 *
 * Additional sizes for TACZ guns are registered at mod load time via
 * {@code TaczCompat.registerSizes()}.
 */
public class GridItemSizes {

    private static final Map<Class<? extends Item>, GridSize> CLASS_SIZES = new HashMap<>();
    private static final Map<Item, GridSize> ITEM_SIZES = new HashMap<>();

    static {
        // Swords / knives
        CLASS_SIZES.put(SwordItem.class,    GridSize.ONE_BY_THREE);
        // Axes
        CLASS_SIZES.put(AxeItem.class,      GridSize.TWO_BY_THREE);
        // Pickaxes / shovels
        CLASS_SIZES.put(PickaxeItem.class,  GridSize.TWO_BY_THREE);
        CLASS_SIZES.put(ShovelItem.class,   GridSize.ONE_BY_THREE);
        CLASS_SIZES.put(HoeItem.class,      GridSize.TWO_BY_TWO);
        // Ranged
        CLASS_SIZES.put(BowItem.class,      GridSize.TWO_BY_THREE);
        CLASS_SIZES.put(CrossbowItem.class, new GridSize(2, 4));
        CLASS_SIZES.put(TridentItem.class,  new GridSize(1, 4));
        // Armor — sizes are determined per ArmorItem.Type at runtime; see getSize()
        CLASS_SIZES.put(ArmorItem.class,    GridSize.TWO_BY_TWO); // fallback
        // Buckets
        CLASS_SIZES.put(BucketItem.class,   GridSize.TWO_BY_TWO);
        // Books
        CLASS_SIZES.put(WritableBookItem.class, GridSize.TWO_BY_ONE);
        CLASS_SIZES.put(WrittenBookItem.class,   GridSize.TWO_BY_ONE);
        // Shields
        CLASS_SIZES.put(ShieldItem.class,   new GridSize(2, 3));
    }

    /** Register a specific item instance with a custom grid size. */
    public static void register(Item item, GridSize size) {
        ITEM_SIZES.put(item, size);
    }

    /** Returns the grid size for the given item. */
    public static GridSize getSize(Item item) {
        // Exact instance match first
        if (ITEM_SIZES.containsKey(item)) {
            return ITEM_SIZES.get(item);
        }

        // Armor — fine-grained by type
        if (item instanceof ArmorItem armor) {
            return switch (armor.getType()) {
                case HELMET    -> new GridSize(2, 2);
                case CHESTPLATE -> new GridSize(3, 2);
                case LEGGINGS  -> new GridSize(2, 3);
                case BOOTS     -> new GridSize(2, 2);
                default        -> GridSize.TWO_BY_TWO;
            };
        }

        // Class hierarchy scan
        for (Map.Entry<Class<? extends Item>, GridSize> entry : CLASS_SIZES.entrySet()) {
            if (entry.getKey().isInstance(item)) {
                return entry.getValue();
            }
        }

        return GridSize.ONE_BY_ONE;
    }
}
