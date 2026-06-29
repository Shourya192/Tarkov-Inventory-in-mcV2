package com.tarkovinventory.event;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.capability.IPlayerEquipment;
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

        // Also capture capability equipment slots so the corpse loot panel
        // can show and let the player take face/ear/rig/backpack items.
        ModCapabilities.get(player).ifPresent(cap -> {
            addSlotted(slotted, "cap.face",  cap.getSlot(IPlayerEquipment.SLOT_FACE));
            addSlotted(slotted, "cap.ear",   cap.getSlot(IPlayerEquipment.SLOT_EARPIECE));
            // Rig & backpack: strip the embedded grid NBT so their contents live ONLY
            // in the corpse's separate rig/grid content lists (single source of truth).
            // The contents are re-packed into the item when it's looted.
            addSlotted(slotted, "cap.rig",   stripGridNbt(cap.getSlot(IPlayerEquipment.SLOT_RIG)));
            addSlotted(slotted, "cap.knee",  cap.getSlot(IPlayerEquipment.SLOT_KNEES));
            addSlotted(slotted, "cap.back",  stripGridNbt(cap.getSlot(IPlayerEquipment.SLOT_ON_BACK)));
        });

        // PRIMARY / SECONDARY weapons live on hotbar slots 0 and 1.
        addSlotted(slotted, "weapon.primary",   player.getInventory().items.get(0));
        addSlotted(slotted, "weapon.secondary", player.getInventory().items.get(1));

        // Main inventory — skip hotbar slots 0 & 1 (PRIMARY/SECONDARY, shown as gear)
        // and slots 2-8 (pockets, captured separately) to avoid duplication.
        List<ItemStack> inventory = new ArrayList<>();
        var invItems = player.getInventory().items;
        for (int i = 9; i < invItems.size(); i++) {
            if (!invItems.get(i).isEmpty()) inventory.add(invItems.get(i).copy());
        }
        PENDING_INVENTORY.put(player.getUUID(), inventory);

        // Pockets: hotbar 2-8 (positional — 7 slots including empties)
        List<ItemStack> pockets = new ArrayList<>();
        for (int i = 2; i <= 8; i++) pockets.add(player.getInventory().items.get(i).copy());
        PENDING_POCKETS.put(player.getUUID(), pockets);

        // Rig contents (from RigContainer) — convert tight cols×rows layout to
        // MAX_COLS-indexed so it matches the corpse display/writeback layout.
        try {
            RigContainer rig = new RigContainer(player, RigContainer.Mode.RIG);
            int rc = rig.getCols(), rr = rig.getRows();
            List<ItemStack> rigItems = maxColsLayout(rig, rc, rr);
            PENDING_RIG.put(player.getUUID(), new Object[]{rigItems, rc, rr});
        } catch (Exception ignored) {}

        // Backpack grid contents — same MAX_COLS conversion.
        try {
            com.tarkovinventory.inventory.RigContainer bp =
                new com.tarkovinventory.inventory.RigContainer(player, com.tarkovinventory.inventory.RigContainer.Mode.BACKPACK);
            int bc = bp.getCols(), br = bp.getRows();
            List<ItemStack> bpItems = maxColsLayout(bp, bc, br);
            PENDING_GRID.put(player.getUUID(), new Object[]{bpItems, bc, br});
        } catch (Exception ignored) {}
    }

    /** Converts a RigContainer's tight cols×rows contents into a MAX_COLS-indexed
     *  flat list (MAX_CELLS long), matching the corpse display layout. */
    private static List<ItemStack> maxColsLayout(RigContainer c, int cols, int rows) {
        ItemStack[] out = new ItemStack[GridInventory.MAX_CELLS];
        java.util.Arrays.fill(out, ItemStack.EMPTY);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int tight = row * cols + col;
                int maxIdx = row * GridInventory.MAX_COLS + col;
                if (tight < c.getContainerSize() && maxIdx < out.length) {
                    out[maxIdx] = c.getItem(tight).copy();
                }
            }
        }
        return new ArrayList<>(java.util.Arrays.asList(out));
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

    /** Returns a copy of the item with its embedded grid inventory NBT removed. */
    private static ItemStack stripGridNbt(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        ItemStack copy = stack.copy();
        if (copy.getTag() != null) {
            copy.getTag().remove("TarkovRigInventory");
            if (copy.getTag().isEmpty()) copy.setTag(null);
        }
        return copy;
    }
}
