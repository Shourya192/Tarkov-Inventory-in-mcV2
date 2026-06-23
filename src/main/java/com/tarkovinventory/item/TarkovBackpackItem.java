package com.tarkovinventory.item;

import com.tarkovinventory.container.TarkovInventoryMenu;
import com.tarkovinventory.inventory.GridInventory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

public class TarkovBackpackItem extends Item {

    public static final String TAG_INVENTORY = "TarkovGrid";

    public TarkovBackpackItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(
            @NotNull Level level,
            @NotNull Player player,
            @NotNull InteractionHand hand) {

        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            int handOrdinal = hand.ordinal();

            NetworkHooks.openScreen(
                    (ServerPlayer) player,
                    new MenuProvider() {
                        @Override
                        public @NotNull Component getDisplayName() {
                            return Component.translatable("container.tarkovinventory.tactical_backpack");
                        }

                        @Override
                        public AbstractContainerMenu createMenu(
                                int windowId,
                                @NotNull Inventory inv,
                                @NotNull Player p) {
                            return new TarkovInventoryMenu(windowId, inv, handOrdinal);
                        }
                    },
                    buf -> buf.writeInt(handOrdinal)
            );
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    // ---------------------------------------------------------------
    // NBT helpers used by the container
    // ---------------------------------------------------------------

    public static GridInventory loadInventory(ItemStack stack) {
        GridInventory inv = new GridInventory();
        CompoundTag tag = stack.getOrCreateTag();
        if (tag.contains(TAG_INVENTORY)) {
            inv.load(tag.getCompound(TAG_INVENTORY));
        }
        return inv;
    }

    public static void saveInventory(ItemStack stack, GridInventory inv) {
        stack.getOrCreateTag().put(TAG_INVENTORY, inv.save());
    }
}
