package com.tarkovinventory.compat;

import com.tarkovinventory.inventory.RigInventory;
import com.tarkovinventory.inventory.RigSizes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public final class BackpackCompat {

    private BackpackCompat() {}

    // ─────────────────────────────
    // MOD DETECTION
    // ─────────────────────────────
    public enum BackpackMod {
        NONE, SOPHISTICATED_BACKPACKS, TRAVELERS_BACKPACK,
        IRON_BACKPACKS, MODERN_MAYHEM, SURVIVORS_ARSENAL
    }

    public static BackpackMod detectMod(ItemStack stack) {
        if (stack.isEmpty()) return BackpackMod.NONE;

        String ns = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).getNamespace();

        return switch (ns) {
            case "sophisticatedbackpacks" -> BackpackMod.SOPHISTICATED_BACKPACKS;
            case "travelersbackpack" -> BackpackMod.TRAVELERS_BACKPACK;
            case "ironbackpacks" -> BackpackMod.IRON_BACKPACKS;
            case "mm" -> BackpackMod.MODERN_MAYHEM;
            case "survivorsarsenal" -> BackpackMod.SURVIVORS_ARSENAL;
            default -> BackpackMod.NONE;
        };
    }

    public static boolean isExternalBackpack(ItemStack stack) {
        return detectMod(stack) != BackpackMod.NONE;
    }

    public static String getExternalLabel(ItemStack stack) {
        return switch (detectMod(stack)) {
            case SOPHISTICATED_BACKPACKS -> "Sophisticated Backpack";
            case TRAVELERS_BACKPACK -> "Traveler's Backpack";
            case IRON_BACKPACKS -> "Iron Backpack";
            case MODERN_MAYHEM -> "Modern Mayhem";
            case SURVIVORS_ARSENAL -> "Survivor's Arsenal";
            default -> null;
        };
    }

    // ─────────────────────────────
    // TRANSACTION CORE
    // ─────────────────────────────
    public static RigTransaction openRig(ItemStack rig) {
        return new RigTransaction(rig);
    }

    public static final class RigTransaction {

        public final ItemStack rig;
        public final CompoundTag tag;
        public final RigInventory inv;

        public RigTransaction(ItemStack rig) {
            this.rig = rig;
            this.tag = rig.getOrCreateTag();

            this.inv = new RigInventory(
                    RigSizes.getCols(rig),
                    RigSizes.getRows(rig)
            );

            if (tag.contains("TarkovRigInventory")) {
                inv.deserializeNBT(tag.getCompound("TarkovRigInventory"));
            }
        }

        public void commit() {
            tag.put("TarkovRigInventory", inv.serializeNBT());
        }

        public boolean isValidSlot(int slot) {
            return slot >= 0 && slot < inv.getSlots();
        }
    }

    // ─────────────────────────────
    // BACK COMPAT HANDLER (UI ONLY SAFE)
    // ─────────────────────────────
    public static IItemHandler getRigInventoryHandler(ItemStack rig) {
        RigTransaction tx = openRig(rig);
        return new RigInventoryHandler(tx);
    }

    private static final class RigInventoryHandler implements IItemHandler {

        private final RigTransaction tx;

        RigInventoryHandler(RigTransaction tx) {
            this.tx = tx;
        }

        @Override public int getSlots() { return tx.inv.getSlots(); }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return tx.inv.getItem(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!simulate) tx.inv.insertItem(slot, stack);
            return ItemStack.EMPTY;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return tx.inv.extractItem(slot, amount, simulate);
        }

        @Override public int getSlotLimit(int slot) { return 64; }

        @Override public boolean isItemValid(int slot, ItemStack stack) { return true; }
    }
}
