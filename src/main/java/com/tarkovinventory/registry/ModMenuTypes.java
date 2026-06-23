package com.tarkovinventory.registry;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.container.TarkovInventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, TarkovInventoryMod.MOD_ID);

    public static final RegistryObject<MenuType<TarkovInventoryMenu>> TARKOV_INVENTORY =
            MENU_TYPES.register("tarkov_inventory",
                    () -> IForgeMenuType.create((windowId, inv, data) -> {
                        int hand = data.readInt();
                        return new TarkovInventoryMenu(windowId, inv, hand);
                    }));
}
