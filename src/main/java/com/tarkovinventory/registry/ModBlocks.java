package com.tarkovinventory.registry;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.block.TarkovCorpseBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, TarkovInventoryMod.MOD_ID);

    /** Placed at the player's feet on death; stores their full inventory. */
    public static final RegistryObject<Block> TARKOV_CORPSE =
            BLOCKS.register("tarkov_corpse", TarkovCorpseBlock::new);
}
