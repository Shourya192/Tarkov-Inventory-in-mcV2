package com.tarkovinventory.registry;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.item.TarkovBackpackItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, TarkovInventoryMod.MOD_ID);

    public static final RegistryObject<Item> TACTICAL_BACKPACK = ITEMS.register(
            "tactical_backpack",
            () -> new TarkovBackpackItem(new Item.Properties().stacksTo(1))
    );
}
