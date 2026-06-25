package com.tarkovinventory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tarkovinventory.capability.IPlayerEquipment;
import com.tarkovinventory.capability.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class TarkovEquipmentLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    public TarkovEquipmentLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light,
                       AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {

        ModCapabilities.get(player).ifPresent(cap -> {
            var model = getParentModel();

            renderOnPart(pose, buffers, light, player, model.head,
                    cap.getSlot(IPlayerEquipment.SLOT_EARPIECE),
                    -0.4f, 0.0f, 0.0f, 0f, 0f, 0f, 0.55f, ItemDisplayContext.HEAD);

            renderOnPart(pose, buffers, light, player, model.head,
                    cap.getSlot(IPlayerEquipment.SLOT_FACE),
                    0.0f, 0.0f, -0.6f, 90f, 0f, 0f, 0.45f, ItemDisplayContext.HEAD);

            renderOnPart(pose, buffers, light, player, model.body,
                    cap.getSlot(IPlayerEquipment.SLOT_RIG),
                    0.0f, -0.1f, -0.4f, 0f, 0f, 0f, 0.65f, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);

            ItemStack knee = cap.getSlot(IPlayerEquipment.SLOT_KNEES);
            if (!knee.isEmpty()) {
                renderOnPart(pose, buffers, light, player, model.leftLeg,  knee,
                        0.0f, 0.2f, -0.25f, 0f, 0f, 0f, 0.45f, ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
                renderOnPart(pose, buffers, light, player, model.rightLeg, knee,
                        0.0f, 0.2f, -0.25f, 0f, 0f, 0f, 0.45f, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
            }
        });
    }

    private void renderOnPart(PoseStack pose, MultiBufferSource buf, int light,
                               AbstractClientPlayer player, ModelPart part, ItemStack stack,
                               float dx, float dy, float dz,
                               float rotX, float rotY, float rotZ,
                               float scale, ItemDisplayContext ctx) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        part.translateAndRotate(pose);
        pose.translate(dx, dy, dz);
        if (rotX != 0) pose.mulPose(Axis.XP.rotationDegrees(rotX));
        if (rotY != 0) pose.mulPose(Axis.YP.rotationDegrees(rotY));
        if (rotZ != 0) pose.mulPose(Axis.ZP.rotationDegrees(rotZ));
        pose.scale(scale, -scale, -scale);
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ctx, light, OverlayTexture.NO_OVERLAY,
                pose, buf, player.level(), player.getId());
        pose.popPose();
    }
}
