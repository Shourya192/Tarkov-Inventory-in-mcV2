package com.tarkovinventory.capability;

import com.tarkovinventory.inventory.RigContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.NotNull;

/**
 * An {@link IItemHandler} that presents the player's vanilla inventory followed
 * by the contents of their equipped TaCZ rig. This lets external mods (e.g. the
 * TaCZ Magazines mod, which searches {@code player.getCapability(ITEM_HANDLER)}
 * for compatible magazines) find and consume magazines stored in the rig.
 *
 * Slot layout:
 *   [0 .. vanillaSize-1]                  → vanilla inventory (player.getInventory())
 *   [vanillaSize .. vanillaSize+rigSize-1] → equipped rig contents
 *
 * Extractions/insertions into the rig region are written straight back to the
 * rig item's NBT via {@link RigContainer}, so changes persist.
 */
public class RigAwareItemHandler implements IItemHandlerModifiable {

    private final Player player;
    private final IItemHandlerModifiable vanilla;

    public RigAwareItemHandler(Player player) {
        this.player = player;
        this.vanilla = new InvWrapper(player.getInventory());
    }

    /** Lazily builds a RigContainer view of the currently-equipped rig. */
    private RigContainer rig() {
        return new RigContainer(player, RigContainer.Mode.RIG);
    }

    private int vanillaSize() { return vanilla.getSlots(); }

    private int rigSize() {
        RigContainer r = rig();
        return r.isItemEquipped() ? r.getContainerSize() : 0;
    }

    @Override
    public int getSlots() {
        return vanillaSize() + rigSize();
    }

    @Override
    public @NotNull ItemStack getStackInSlot(int slot) {
        int v = vanillaSize();
        if (slot < v) return vanilla.getStackInSlot(slot);
        int ri = slot - v;
        RigContainer r = rig();
        if (!r.isItemEquipped() || ri >= r.getContainerSize()) return ItemStack.EMPTY;
        return r.getItem(ri);
    }

    @Override
    public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        int v = vanillaSize();
        if (slot < v) return vanilla.insertItem(slot, stack, simulate);
        int ri = slot - v;
        RigContainer r = rig();
        if (!r.isItemEquipped() || ri >= r.getContainerSize()) return stack;
        ItemStack existing = r.getItem(ri);
        if (existing.isEmpty()) {
            int limit = Math.min(stack.getMaxStackSize(), getSlotLimit(slot));
            int toPlace = Math.min(stack.getCount(), limit);
            if (!simulate) {
                ItemStack placed = stack.copy();
                placed.setCount(toPlace);
                r.setItem(ri, placed);
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(toPlace);
            return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
        }
        if (ItemStack.isSameItemSameTags(existing, stack)) {
            int limit = Math.min(existing.getMaxStackSize(), getSlotLimit(slot));
            int space = limit - existing.getCount();
            int toPlace = Math.min(space, stack.getCount());
            if (toPlace <= 0) return stack;
            if (!simulate) {
                ItemStack updated = existing.copy();
                updated.grow(toPlace);
                r.setItem(ri, updated);
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(toPlace);
            return remainder.isEmpty() ? ItemStack.EMPTY : remainder;
        }
        return stack;
    }

    @Override
    public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
        int v = vanillaSize();
        if (slot < v) return vanilla.extractItem(slot, amount, simulate);
        int ri = slot - v;
        RigContainer r = rig();
        if (!r.isItemEquipped() || ri >= r.getContainerSize()) return ItemStack.EMPTY;
        ItemStack existing = r.getItem(ri);
        if (existing.isEmpty()) return ItemStack.EMPTY;
        int toTake = Math.min(amount, existing.getCount());
        ItemStack result = existing.copy();
        result.setCount(toTake);
        if (!simulate) {
            ItemStack remaining = existing.copy();
            remaining.shrink(toTake);
            r.setItem(ri, remaining.isEmpty() ? ItemStack.EMPTY : remaining);
        }
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        int v = vanillaSize();
        if (slot < v) return vanilla.getSlotLimit(slot);
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        int v = vanillaSize();
        if (slot < v) return vanilla.isItemValid(slot, stack);
        return true;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        int v = vanillaSize();
        if (slot < v) { vanilla.setStackInSlot(slot, stack); return; }
        int ri = slot - v;
        RigContainer r = rig();
        if (r.isItemEquipped() && ri < r.getContainerSize()) r.setItem(ri, stack);
    }
}
