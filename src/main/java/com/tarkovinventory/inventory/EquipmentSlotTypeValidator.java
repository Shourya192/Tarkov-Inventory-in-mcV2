package com.tarkovinventory.inventory;

import com.tarkovinventory.client.screen.modules.EquipmentSlotType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Extensible equipment-slot validation system.
 *
 * Determines whether an ItemStack is valid for a given EquipmentSlotType.
 * Uses a combination of:
 *   - Vanilla class checks (ArmorItem, SwordItem, etc.)
 *   - Item tag checks (forge:helmets, forge:weapons, etc.)
 *   - Custom registered predicates (for mod compatibility)
 *
 * Other mods can register validators via {@link #registerValidator}.
 */
public final class EquipmentSlotTypeValidator {

    private EquipmentSlotTypeValidator() {}

    /** Custom validators registered by mods or internal setup. */
    private static final Map<EquipmentSlotType, List<Predicate<ItemStack>>> CUSTOM_VALIDATORS = new HashMap<>();

    static {
        // ── Default vanilla-based validators ──────────────────────────

        // HEAD: helmets
        registerValidator(EquipmentSlotType.HEAD, stack -> {
            if (stack.getItem() instanceof ArmorItem armor)
                return armor.getType() == ArmorItem.Type.HELMET;
            return isInTag(stack, "forge:helmets");
        });

        // FACE: custom Tarkov slot — no vanilla tag equivalent, accept anything
        registerValidator(EquipmentSlotType.FACE, stack -> true);

        // EAR: custom Tarkov slot — accept anything
        registerValidator(EquipmentSlotType.EAR, stack -> true);

        // EYES: eyewear (tag only)
        registerValidator(EquipmentSlotType.EYES, stack ->
                isInTag(stack, "forge:eye_armor") || isInTag(stack, "tarkov:eyes"));

        // ARMOR: chestplates
        registerValidator(EquipmentSlotType.ARMOR, stack -> {
            if (stack.getItem() instanceof ArmorItem armor)
                return armor.getType() == ArmorItem.Type.CHESTPLATE;
            return isInTag(stack, "forge:chestplates") || isInTag(stack, "forge:chest_armor");
        });

        // RIG: only items registered as rigs (in RigSizes) may go in the rig slot.
        registerValidator(EquipmentSlotType.RIG, stack ->
                RigSizes.isRegistered(stack) || isInTag(stack, "tarkov:rigs"));

        // PANTS: leg armor
        registerValidator(EquipmentSlotType.PANTS, stack -> {
            if (stack.getItem() instanceof ArmorItem armor)
                return armor.getType() == ArmorItem.Type.LEGGINGS;
            return isInTag(stack, "forge:leggings") || isInTag(stack, "forge:leg_armor");
        });

        // KNEE: custom Tarkov slot — accept anything
        registerValidator(EquipmentSlotType.KNEE, stack -> true);

        // BOOTS: boots
        registerValidator(EquipmentSlotType.BOOTS, stack -> {
            if (stack.getItem() instanceof ArmorItem armor)
                return armor.getType() == ArmorItem.Type.BOOTS;
            return isInTag(stack, "forge:boots") || isInTag(stack, "forge:feet_armor");
        });

        // BACKPACK: only items registered as backpacks (in BackpackSizes) may go here.
        registerValidator(EquipmentSlotType.BACKPACK, stack ->
                BackpackSizes.isRegistered(stack) || isInTag(stack, "tarkov:backpacks"));

        // WEAPON: any weapon
        registerValidator(EquipmentSlotType.WEAPON, stack -> {
            Item item = stack.getItem();
            if (item instanceof SwordItem || item instanceof BowItem
                    || item instanceof CrossbowItem || item instanceof TridentItem) return true;
            return isInTag(stack, "forge:weapons") || isInTag(stack, "forge:tools/melee");
        });

        // UNKNOWN: accept anything
        registerValidator(EquipmentSlotType.UNKNOWN, stack -> true);
    }

    /**
     * Returns true if the given stack is valid for the specified equipment slot type.
     */
    public static boolean isValid(ItemStack stack, EquipmentSlotType type) {
        if (stack == null || stack.isEmpty()) return true; // empty is always valid
        if (type == null) return false;

        // Tag-only mode: an item fits a slot ONLY if an admin has explicitly assigned
        // it that slot type via the tagging UI. Items with no assignment fit nothing.
        String assigned = com.tarkovinventory.config.ItemTypeConfig.getType(stack);
        if (assigned == null) return false;
        return assigned.equalsIgnoreCase(type.name());
    }

    /**
     * Register an additional validation predicate for a slot type.
     * Multiple predicates per type are OR'd together.
     */
    public static void registerValidator(EquipmentSlotType type, Predicate<ItemStack> validator) {
        CUSTOM_VALIDATORS.computeIfAbsent(type, k -> new ArrayList<>()).add(validator);
    }

    // ── Tag helper ──────────────────────────────────────────────────

    private static boolean isInTag(ItemStack stack, String tagStr) {
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation tagId = ResourceLocation.tryParse(tagStr);
        if (tagId == null) return false;
        TagKey<Item> tag = TagKey.create(ForgeRegistries.ITEMS.getRegistryKey(), tagId);
        return stack.is(tag);
    }

    /**
     * Convenience: get the vanilla EquipmentSlot for an EquipmentSlotType,
     * or null if no direct mapping exists.
     */
    public static EquipmentSlot toVanillaSlot(EquipmentSlotType type) {
        return switch (type) {
            case HEAD   -> EquipmentSlot.HEAD;
            case ARMOR  -> EquipmentSlot.CHEST;
            case PANTS  -> EquipmentSlot.LEGS;
            case BOOTS  -> EquipmentSlot.FEET;
            default     -> null;
        };
    }
}
