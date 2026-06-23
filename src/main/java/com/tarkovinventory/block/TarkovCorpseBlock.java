package com.tarkovinventory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A corpse block placed at a player's death location.
 *
 * <ul>
 *   <li>Cannot be obtained as a regular item (loot table is empty).</li>
 *   <li>Placed programmatically by {@link com.tarkovinventory.event.PlayerDeathHandler}.</li>
 *   <li>When the block is removed (broken or all items taken), its stored
 *       inventory is scattered on the ground.</li>
 *   <li>Flat slab shape (8-pixel tall) so it looks like a body on the ground.</li>
 * </ul>
 */
public class TarkovCorpseBlock extends BaseEntityBlock {

    public TarkovCorpseBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .noOcclusion()
                .requiresCorrectToolForDrops());
    }

    // ── Block entity ─────────────────────────────────────────────────

    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TarkovCorpseBlockEntity(pos, state);
    }

    // ── Item scattering on break ──────────────────────────────────────

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level,
                         @NotNull BlockPos pos, @NotNull BlockState newState, boolean piston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TarkovCorpseBlockEntity be) {
                be.getSlottedItems().values().forEach(stack -> popResource(level, pos, stack));
                be.getInventoryItems().forEach(stack -> popResource(level, pos, stack));
            }
        }
        super.onRemove(state, level, pos, newState, piston);
    }
}
