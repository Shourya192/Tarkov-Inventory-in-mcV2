package com.tarkovinventory.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Soft compatibility with Curios API.
 * Uses pure reflection — no Curios classes are imported or referenced at
 * compile time, so the mod compiles and runs without Curios on the classpath.
 *
 * Tries four patterns across all Curios 5.x builds for Forge 1.20.1:
 *   A) CuriosApi.getCuriosInventory(LivingEntity) → LazyOptional  — via orElse(null)
 *   B) CuriosApi.getCuriosInventory(LivingEntity) → LazyOptional  — via resolve()
 *   C) getCuriosHelper().getCuriosHandler(LivingEntity)
 *   D) getCuriosHelper().getCuriosHandler(Player)
 */
public final class CuriosCompat {

    private CuriosCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("curios");
    }

    // ── Core: resolve the ICuriosItemHandler for a player ─────────────

    /**
     * Returns the raw ICuriosItemHandler for the given player, or null if
     * Curios is absent or every known API pattern fails.
     */
    public static Object getHandler(Player player) {
        if (!isLoaded()) return null;
        try {
            Class<?> api = Class.forName("top.theillusivec4.curios.api.CuriosApi");

            // ── Pattern A: getCuriosInventory(LivingEntity) — orElse ──────
            try {
                Method m = api.getMethod("getCuriosInventory", LivingEntity.class);
                Object lazyOpt = m.invoke(null, player);
                try {
                    Method orElse = lazyOpt.getClass().getMethod("orElse", Object.class);
                    Object handler = orElse.invoke(lazyOpt, (Object) null);
                    if (handler != null) return handler;
                } catch (NoSuchMethodException ignored) {}
                try {
                    Method resolve = lazyOpt.getClass().getMethod("resolve");
                    Optional<?> opt = (Optional<?>) resolve.invoke(lazyOpt);
                    if (opt.isPresent()) return opt.get();
                } catch (NoSuchMethodException ignored) {}
            } catch (NoSuchMethodException ignored) {}

            // ── Pattern B: getCuriosHelper().getCuriosHandler(LivingEntity)
            try {
                Object helper = api.getMethod("getCuriosHelper").invoke(null);
                for (Class<?> paramType : new Class<?>[] { LivingEntity.class, Player.class }) {
                    try {
                        Method hm = helper.getClass().getMethod("getCuriosHandler", paramType);
                        Object lazyOpt = hm.invoke(helper, player);
                        try {
                            Method orElse = lazyOpt.getClass().getMethod("orElse", Object.class);
                            Object handler = orElse.invoke(lazyOpt, (Object) null);
                            if (handler != null) return handler;
                        } catch (NoSuchMethodException ignored) {}
                        try {
                            Method resolve = lazyOpt.getClass().getMethod("resolve");
                            Optional<?> opt = (Optional<?>) resolve.invoke(lazyOpt);
                            if (opt.isPresent()) return opt.get();
                        } catch (NoSuchMethodException ignored) {}
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (NoSuchMethodException ignored) {}

            // ── Pattern C: Forge capability approach ──────────────────────
            try {
                Class<?> capClass = Class.forName("top.theillusivec4.curios.api.CuriosCapability");
                java.lang.reflect.Field f = capClass.getDeclaredField("ITEM_HANDLER");
                f.setAccessible(true);
                Object capKey = f.get(null);
                Method getCap = player.getClass().getMethod("getCapability",
                        Class.forName("net.minecraftforge.common.capabilities.Capability"));
                Object lazyOpt = getCap.invoke(player, capKey);
                if (lazyOpt != null) {
                    try {
                        Method orElse = lazyOpt.getClass().getMethod("orElse", Object.class);
                        Object h = orElse.invoke(lazyOpt, (Object) null);
                        if (h != null) return h;
                    } catch (NoSuchMethodException ignored) {}
                    try {
                        Method resolve = lazyOpt.getClass().getMethod("resolve");
                        Optional<?> opt = (Optional<?>) resolve.invoke(lazyOpt);
                        if (opt.isPresent()) return opt.get();
                    } catch (NoSuchMethodException ignored) {}
                }
            } catch (Throwable ignored) {}

        } catch (Throwable ignored) {}
        return null;
    }

    // ── Public helpers ────────────────────────────────────────────────

    /**
     * Returns all non-empty stacks currently equipped in any Curios slot.
     * Returns an empty list if Curios is absent or an error occurs.
     *
     * Tries two approaches for extracting stacks:
     *   1. handler.getCurios()            → Map of ICurioStacksHandler
     *   2. handler.getEquippedCurios()    → flat Multimap / Iterable (newer builds)
     */
    public static List<CuriosSlotEntry> getEquippedSlots(Player player) {
        List<CuriosSlotEntry> result = new ArrayList<>();
        Object handler = getHandler(player);
        if (handler == null) return result;

        // ── Approach 1: getCurios() → Map<String, ICurioStacksHandler> ───
        try {
            Method getCurios = handler.getClass().getMethod("getCurios");
            Map<?, ?> curios = (Map<?, ?>) getCurios.invoke(handler);
            for (Map.Entry<?, ?> entry : curios.entrySet()) {
                String slotId = entry.getKey().toString();
                Object stacksHandler = entry.getValue();

                // Try getStacks() first, then getEquippedStacks()
                Object stacksObj = null;
                for (String methodName : new String[] { "getStacks", "getEquippedStacks", "getCosmeticStacks" }) {
                    try {
                        stacksObj = stacksHandler.getClass().getMethod(methodName).invoke(stacksHandler);
                        break;
                    } catch (NoSuchMethodException ignored) {}
                }
                if (stacksObj == null) continue;

                int slots = (int) stacksObj.getClass().getMethod("getSlots").invoke(stacksObj);
                Method getStack = stacksObj.getClass().getMethod("getStackInSlot", int.class);
                for (int i = 0; i < slots; i++) {
                    ItemStack stack = (ItemStack) getStack.invoke(stacksObj, i);
                    if (!stack.isEmpty()) result.add(new CuriosSlotEntry(slotId, i, stack));
                }
            }
            if (!result.isEmpty()) return result;
        } catch (Throwable ignored) {}

        // ── Approach 2: getEquippedCurios() → Iterable of slot-result objects
        try {
            Method getEquipped = handler.getClass().getMethod("getEquippedCurios");
            Object multimap = getEquipped.invoke(handler);
            // Multimap<String, SlotResult> — iterate values()
            Method values = multimap.getClass().getMethod("values");
            Iterable<?> vals = (Iterable<?>) values.invoke(multimap);
            for (Object slotResult : vals) {
                // SlotResult has slotContext() and stack()
                try {
                    Object ctx = slotResult.getClass().getMethod("slotContext").invoke(slotResult);
                    String slotId = (String) ctx.getClass().getMethod("identifier").invoke(ctx);
                    int index     = (int) ctx.getClass().getMethod("index").invoke(ctx);
                    ItemStack s   = (ItemStack) slotResult.getClass().getMethod("stack").invoke(slotResult);
                    if (!s.isEmpty()) result.add(new CuriosSlotEntry(slotId, index, s));
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        return result;
    }

    /**
     * Returns the first non-empty item in a specific Curios slot, or EMPTY.
     * Lookup is case-insensitive so "Facewear" matches "facewear".
     */
    public static ItemStack getSlotItem(Player player, String slotId, int index) {
        Object handler = getHandler(player);
        if (handler == null) return ItemStack.EMPTY;
        try {
            Map<?, ?> curios = (Map<?, ?>) handler.getClass().getMethod("getCurios").invoke(handler);
            Object stacksHandler = findSlot(curios, slotId);
            if (stacksHandler == null) return ItemStack.EMPTY;
            Object stacks = null;
            for (String mn : new String[] { "getStacks", "getEquippedStacks" }) {
                try { stacks = stacksHandler.getClass().getMethod(mn).invoke(stacksHandler); break; }
                catch (NoSuchMethodException ignored) {}
            }
            if (stacks == null) return ItemStack.EMPTY;
            return (ItemStack) stacks.getClass()
                .getMethod("getStackInSlot", int.class).invoke(stacks, index);
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    /**
     * Set an item in a specific Curios slot. No-op if Curios is absent.
     * Lookup is case-insensitive.
     */
    public static void setSlot(Player player, String slotId, int index, ItemStack stack) {
        Object handler = getHandler(player);
        if (handler == null) return;
        try {
            Map<?, ?> curios = (Map<?, ?>) handler.getClass().getMethod("getCurios").invoke(handler);
            Object slotHandler = findSlot(curios, slotId);
            if (slotHandler == null) return;
            Object stacks = null;
            for (String mn : new String[] { "getStacks", "getEquippedStacks" }) {
                try { stacks = slotHandler.getClass().getMethod(mn).invoke(slotHandler); break; }
                catch (NoSuchMethodException ignored) {}
            }
            if (stacks == null) return;
            stacks.getClass().getMethod("setStackInSlot", int.class, ItemStack.class)
                .invoke(stacks, index, stack);
        } catch (Throwable ignored) {}
    }

    /**
     * Case-insensitive slot lookup in a Curios curios map.
     * Tries exact match first, then case-insensitive fallback.
     */
    private static Object findSlot(Map<?, ?> curios, String slotId) {
        // Exact match first
        Object result = curios.get(slotId);
        if (result != null) return result;
        // Case-insensitive fallback
        for (Map.Entry<?, ?> entry : curios.entrySet()) {
            if (entry.getKey().toString().equalsIgnoreCase(slotId)) return entry.getValue();
        }
        return null;
    }

    /**
     * Returns the set of curios slot-type IDs that exist on this player,
     * regardless of whether those slots currently hold an item.
     * Used by the screen to decide whether to write to curios vs. vanilla fallback.
     */
    public static Set<String> getAllSlotIds(Player player) {
        Object handler = getHandler(player);
        if (handler == null) return Set.of();
        try {
            Map<?, ?> curios = (Map<?, ?>) handler.getClass().getMethod("getCurios").invoke(handler);
            return curios.keySet().stream().map(Object::toString).collect(Collectors.toSet());
        } catch (Throwable ignored) {}
        return Set.of();
    }

    /** Returns a human-readable label for a Curios slot id. */
    public static String labelFor(String slotId) {
        return switch (slotId) {
            case "head"     -> "HEADWEAR";
            case "back"     -> "ON BACK";
            case "body"     -> "BODY ARMOR";
            case "earwear"  -> "EARWEAR";
            case "facewear" -> "FACEWEAR";
            case "knees"    -> "KNEES";
            case "face"     -> "FACE COVER";
            case "necklace" -> "NECKLACE";
            case "ring"     -> "RING";
            case "hands"    -> "GLOVES";
            case "belt"     -> "BELT";
            case "charm"    -> "CHARM";
            default         -> slotId.toUpperCase();
        };
    }

    public record CuriosSlotEntry(String slotId, int index, ItemStack stack) {}
}
