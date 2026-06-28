package com.tarkovinventory.registry;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TarkovInventoryMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<TarkovCorpseBlockEntity>> TARKOV_CORPSE =
            BLOCK_ENTITIES.register("tarkov_corpse", () ->
                    BlockEntityType.Builder
                            .of(TarkovCorpseBlockEntity::new, ModBlocks.TARKOV_CORPSE.get())
                            .build(null));
}
