package com.tarkovinventory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tarkovinventory.block.TarkovCorpseBlock;
import com.tarkovinventory.block.TarkovCorpseBlockEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Renders a humanoid body lying face-down on the ground for the corpse block.
 *
 * The vanilla player skin texture for "steve" is used as a neutral default.
 * The model is rotated 90° onto its front and oriented by the block's FACING
 * so it looks like a body collapsed in the direction the player was facing.
 */
public class TarkovCorpseRenderer implements BlockEntityRenderer<TarkovCorpseBlockEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("textures/entity/player/wide/steve.png");

    private final HumanoidModel<?> model;

    public TarkovCorpseRenderer(BlockEntityRendererProvider.Context ctx) {
        this.model = new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER));
    }

    @Override
    public void render(TarkovCorpseBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {

        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(TarkovCorpseBlock.FACING)
                ? state.getValue(TarkovCorpseBlock.FACING) : Direction.NORTH;

        pose.pushPose();

        // Centre on the block, sit just above the floor
        pose.translate(0.5, 0.1, 0.5);

        // Orient by facing direction
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));

        // Lay the model flat on its front (tip it 90° forward)
        pose.mulPose(Axis.XP.rotationDegrees(90.0f));

        // Models are 2 units tall in 1/16 scale; shift so it lies centred
        pose.translate(0.0, -1.5, 0.0);
        pose.scale(-1.0f, -1.0f, 1.0f); // standard humanoid model flip

        model.young = false;
        var vc = buffers.getBuffer(RenderType.entitySolid(TEXTURE));
        model.renderToBuffer(pose, vc, packedLight, OverlayTexture.NO_OVERLAY,
                1.0f, 1.0f, 1.0f, 1.0f);

        pose.popPose();
    }
}
