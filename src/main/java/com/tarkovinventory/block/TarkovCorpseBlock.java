package com.tarkovinventory.block;

import com.tarkovinventory.container.TarkovInventoryMenu;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A corpse placed at a player's death location.
 *
 * Rendered as a flat, body-shaped object lying on the ground (a thin slab that
 * faces the direction the player was looking). A custom entity model is supplied
 * by {@link com.tarkovinventory.client.render.TarkovCorpseRenderer}; the block
 * itself uses an invisible model so only the rendered body shows.
 *
 * Right-clicking opens the Tarkov inventory with the corpse's loot in the LOOT
 * panel (handled here in {@link #use}).
 */
public class TarkovCorpseBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // Flat body-shaped collision box (2px tall, full footprint)
    private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.5, 1.0);

    public TarkovCorpseBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(2.0f, 6.0f)
                .noOcclusion()
                .noLootTable());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // ── State ─────────────────────────────────────────────────────────

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level,
                                        @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return SHAPE;
    }

    // The body is drawn by an EntityRenderer-style BER, so the block model is invisible
    @Override
    public @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TarkovCorpseBlockEntity(pos, state);
    }

    // ── Right-click: open Tarkov inventory with corpse loot ───────────

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level,
                                          @NotNull BlockPos pos, @NotNull Player player,
                                          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof TarkovCorpseBlockEntity be)) return InteractionResult.PASS;

        // Always close and reopen so the corpse loot loads correctly
        // even if the player already has the Tarkov inventory open.
        ItemStack carried = sp.containerMenu.getCarried();
        if (!carried.isEmpty()) {
            if (!sp.getInventory().add(carried.copy())) sp.drop(carried.copy(), false);
            sp.containerMenu.setCarried(ItemStack.EMPTY);
        }
        sp.closeContainer();
        NetworkHooks.openScreen(sp,
                new TarkovInventoryMenu.LootMenuProvider(pos.immutable(), true),
                buf -> TarkovInventoryMenu.writeDimensions(buf, sp, pos.immutable(), true));
        return InteractionResult.CONSUME;
    }

    // ── Item scattering on break ──────────────────────────────────────

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level,
                         @NotNull BlockPos pos, @NotNull BlockState newState, boolean piston) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TarkovCorpseBlockEntity be) {
                List<net.minecraft.world.item.ItemStack> all = new ArrayList<>(be.getSlottedItems().values());
                all.addAll(be.getInventoryItems());
                all.forEach(stack -> popResource(level, pos, stack));
            }
        }
        super.onRemove(state, level, pos, newState, piston);
    }
}
