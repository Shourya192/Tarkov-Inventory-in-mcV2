package com.tarkovinventory.event;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import com.tarkovinventory.capability.ModCapabilities;
import com.tarkovinventory.compat.CuriosCompat;
import com.tarkovinventory.inventory.GridInventory;
import com.tarkovinventory.inventory.RigContainer;
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

@Mod.EventBusSubscriber(modid = TarkovInventoryMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerDeathHandler {

    private PlayerDeathHandler() {}

    private static final ConcurrentHashMap<UUID, Map<String, ItemStack>> PENDING_SLOTTED    = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, List<ItemStack>>        PENDING_INVENTORY  = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, List<ItemStack>>        PENDING_POCKETS    = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Object[]>               PENDING_RIG        = new ConcurrentHashMap<>(); // {items, cols, rows}
    private static final ConcurrentHashMap<UUID, Object[]>               PENDING_GRID       = new ConcurrentHashMap<>(); // {items, cols, rows}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Map<String, ItemStack> slotted = new LinkedHashMap<>();

        // Vanilla armor + offhand
        addSlotted(slotted, "armor.head",  player.getItemBySlot(EquipmentSlot.HEAD));
        addSlotted(slotted, "armor.chest", player.getItemBySlot(EquipmentSlot.CHEST));
        addSlotted(slotted, "armor.legs",  player.getItemBySlot(EquipmentSlot.LEGS));
        addSlotted(slotted, "armor.feet",  player.getItemBySlot(EquipmentSlot.FEET));
        addSlotted(slotted, "offhand",     player.getItemBySlot(EquipmentSlot.OFFHAND));

        // Curios slots
        for (CuriosCompat.CuriosSlotEntry e : CuriosCompat.getEquippedSlots(player)) {
            if (!e.stack().isEmpty()) slotted.put("curios." + e.slotId(), e.stack().copy());
        }

        PENDING_SLOTTED.put(player.getUUID(), slotted);

        // Main inventory (36 slots)
        List<ItemStack> inventory = new ArrayList<>();
        for (ItemStack s : player.getInventory().items) {
            if (!s.isEmpty()) inventory.add(s.copy());
        }
        PENDING_INVENTORY.put(player.getUUID(), inventory);

        // Pockets: hotbar 2-8 (positional — 7 slots including empties)
        List<ItemStack> pockets = new ArrayList<>();
        for (int i = 2; i <= 8; i++) pockets.add(player.getInventory().items.get(i).copy());
        PENDING_POCKETS.put(player.getUUID(), pockets);

        // Rig contents (from RigContainer)
        try {
            RigContainer rig = new RigContainer(player, RigContainer.Mode.RIG);
            List<ItemStack> rigItems = new ArrayList<>();
            for (int i = 0; i < rig.getContainerSize(); i++) rigItems.add(rig.getItem(i).copy());
            PENDING_RIG.put(player.getUUID(), new Object[]{rigItems, rig.getCols(), rig.getRows()});
        } catch (Exception ignored) {}

        // Backpack grid contents — read from the equipped backpack item's NBT
        // (same mechanism as the rig), NOT from the capability GridInventory.
        try {
            com.tarkovinventory.inventory.RigContainer bp =
                new com.tarkovinventory.inventory.RigContainer(player, com.tarkovinventory.inventory.RigContainer.Mode.BACKPACK);
            List<ItemStack> bpItems = new ArrayList<>();
            for (int i = 0; i < bp.getContainerSize(); i++) bpItems.add(bp.getItem(i).copy());
            PENDING_GRID.put(player.getUUID(), new Object[]{bpItems, bp.getCols(), bp.getRows()});
        } catch (Exception ignored) {}
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        Map<String, ItemStack> slotted   = PENDING_SLOTTED.remove(player.getUUID());
        List<ItemStack>        inventory = PENDING_INVENTORY.remove(player.getUUID());
        List<ItemStack>        pockets   = PENDING_POCKETS.remove(player.getUUID());
        Object[]               rigData   = PENDING_RIG.remove(player.getUUID());
        Object[]               gridData  = PENDING_GRID.remove(player.getUUID());

        if (slotted == null) slotted = new LinkedHashMap<>();
        if (inventory == null) inventory = new ArrayList<>();

        if (slotted.isEmpty() && inventory.isEmpty()) return;

        BlockPos pos = player.blockPosition();
        for (int dy = 0; dy <= 2; dy++) {
            BlockPos c = pos.above(dy);
            BlockState bs = level.getBlockState(c);
            if (bs.isAir() || bs.canBeReplaced()) { pos = c; break; }
        }

        final BlockPos finalPos = pos;
        BlockState corpseState = ModBlocks.TARKOV_CORPSE.get().defaultBlockState()
                .setValue(com.tarkovinventory.block.TarkovCorpseBlock.FACING, player.getDirection());
        level.setBlock(finalPos, corpseState, 3);
        if (level.getBlockEntity(finalPos) instanceof TarkovCorpseBlockEntity be) {
            be.setOwnerName(player.getGameProfile().getName());
            be.setSlottedItems(slotted);
            be.setInventoryItems(inventory);
            if (pockets != null) be.setPockets(pockets);
            if (rigData  != null) be.setRigContents((List<ItemStack>) rigData[0],  (int) rigData[1],  (int) rigData[2]);
            if (gridData != null) be.setGridContents((List<ItemStack>) gridData[0], (int) gridData[1], (int) gridData[2]);
        }

        event.setCanceled(true);
    }

    private static void addSlotted(Map<String, ItemStack> map, String key, ItemStack stack) {
        if (!stack.isEmpty()) map.put(key, stack.copy());
    }
}
