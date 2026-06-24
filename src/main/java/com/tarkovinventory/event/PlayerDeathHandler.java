package com.tarkovinventory.event;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import com.tarkovinventory.compat.CuriosCompat;
import com.tarkovinventory.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Captures player inventory on death, stores it in a TarkovCorpseBlock, and
 * cancels the vanilla item-scatter.
 *
 * Two-phase approach:
 *   1. LivingDeathEvent (HIGHEST) — capture EVERYTHING while the player is
 *      still fully alive: armor, offhand, curios, AND main inventory.
 *      This is the only safe point; by LivingDropsEvent the inventory is gone.
 *   2. LivingDropsEvent (HIGH)    — cancel the vanilla drops; use the
 *      pre-captured data to build the structured corpse block.
 */
@Mod.EventBusSubscriber(modid = TarkovInventoryMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerDeathHandler {

    private PlayerDeathHandler() {}

    /** Temporary storage keyed by player UUID between the two event phases. */
    private static final ConcurrentHashMap<UUID, Map<String, ItemStack>> PENDING_SLOTTED =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, List<ItemStack>> PENDING_INVENTORY =
            new ConcurrentHashMap<>();

    // ── Phase 1: capture everything while the player still has it ──────

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Map<String, ItemStack> slotted = new LinkedHashMap<>();

        // Vanilla armor + offhand (still intact at HIGHEST priority)
        addSlotted(slotted, "armor.head",  player.getItemBySlot(EquipmentSlot.HEAD));
        addSlotted(slotted, "armor.chest", player.getItemBySlot(EquipmentSlot.CHEST));
        addSlotted(slotted, "armor.legs",  player.getItemBySlot(EquipmentSlot.LEGS));
        addSlotted(slotted, "armor.feet",  player.getItemBySlot(EquipmentSlot.FEET));
        addSlotted(slotted, "offhand",     player.getItemBySlot(EquipmentSlot.OFFHAND));

        // Curios slots (soft-dep; no-ops if Curios is absent)
        for (CuriosCompat.CuriosSlotEntry e : CuriosCompat.getEquippedSlots(player)) {
            if (!e.stack().isEmpty())
                slotted.put("curios." + e.slotId(), e.stack().copy());
        }

        PENDING_SLOTTED.put(player.getUUID(), slotted);

        // Main inventory: capture NOW while it's still intact.
        // By the time LivingDropsEvent fires, vanilla has already cleared these.
        List<ItemStack> inventory = new ArrayList<>();
        for (ItemStack s : player.getInventory().items) {         // 36 slots (hotbar + main)
            if (!s.isEmpty()) inventory.add(s.copy());
        }
        PENDING_INVENTORY.put(player.getUUID(), inventory);
    }

    // ── Phase 2: cancel drops and build the corpse block ──────────────

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        Map<String, ItemStack> slotted   = PENDING_SLOTTED.remove(player.getUUID());
        List<ItemStack>        inventory = PENDING_INVENTORY.remove(player.getUUID());
        if (slotted   == null) slotted   = new LinkedHashMap<>();
        if (inventory == null) inventory = new ArrayList<>();

        if (slotted.isEmpty() && inventory.isEmpty()) return;

        // Find a valid placement spot (prefer the player's feet block, then up)
        BlockPos pos = player.blockPosition();
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos c = pos.above(dy);
            BlockState bs = level.getBlockState(c);
            if (bs.isAir() || bs.canBeReplaced()) { pos = c; break; }
        }

        final BlockPos finalPos = pos;
        BlockState corpseState = ModBlocks.TARKOV_CORPSE.get().defaultBlockState()
                .setValue(com.tarkovinventory.block.TarkovCorpseBlock.FACING,
                        player.getDirection());
        level.setBlock(finalPos, corpseState, 3);
        if (level.getBlockEntity(finalPos) instanceof TarkovCorpseBlockEntity be) {
            be.setOwnerName(player.getGameProfile().getName());
            be.setSlottedItems(slotted);
            be.setInventoryItems(inventory);
        }

        // Cancel vanilla item-scatter so nothing hits the ground
        event.setCanceled(true);
    }

    private static void addSlotted(Map<String, ItemStack> map, String key, ItemStack stack) {
        if (!stack.isEmpty()) map.put(key, stack.copy());
    }
}
