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

/**
 * Renders custom Tarkov equipment on the player model.
 *
 * All items use FIXED display context so the model transform is ignored and
 * our manual offsets control exactly where the item appears.
 * Positive scales are used throughout — negative values were causing items
 * to flip inside the model geometry.
 */
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
            PlayerModel<AbstractClientPlayer> model = getParentModel();

            // EAR / Headset — left side of head
            on(pose, buffers, light, player, model.head,
                    cap.getSlot(IPlayerEquipment.SLOT_EARPIECE),
                    -0.45f, 0.05f, 0.0f,   // offset: left of head center
                    0f, 0f, 0f,
                    0.45f);

            // FACE — front of head
            on(pose, buffers, light, player, model.head,
                    cap.getSlot(IPlayerEquipment.SLOT_FACE),
                    0.0f, 0.05f, -0.55f,   // pushed forward
                    0f, 0f, 0f,
                    0.45f);

            // RIG — front of torso
            on(pose, buffers, light, player, model.body,
                    cap.getSlot(IPlayerEquipment.SLOT_RIG),
                    0.0f, 0.0f, -0.38f,
                    0f, 0f, 0f,
                    0.65f);

            // KNEE pads — both legs
            ItemStack knee = cap.getSlot(IPlayerEquipment.SLOT_KNEES);
            if (!knee.isEmpty()) {
                on(pose, buffers, light, player, model.leftLeg,  knee,
                        0.0f, 0.22f, -0.22f, 0f, 0f, 0f, 0.42f);
                on(pose, buffers, light, player, model.rightLeg, knee,
                        0.0f, 0.22f, -0.22f, 0f, 0f, 0f, 0.42f);
            }
        });
    }

    private void on(PoseStack pose, MultiBufferSource buf, int light,
                    AbstractClientPlayer player, ModelPart part, ItemStack stack,
                    float dx, float dy, float dz,
                    float rotX, float rotY, float rotZ,
                    float scale) {
        if (stack.isEmpty()) return;
        pose.pushPose();
        part.translateAndRotate(pose);
        pose.translate(dx, dy, dz);
        if (rotX != 0) pose.mulPose(Axis.XP.rotationDegrees(rotX));
        if (rotY != 0) pose.mulPose(Axis.YP.rotationDegrees(rotY));
        if (rotZ != 0) pose.mulPose(Axis.ZP.rotationDegrees(rotZ));
        // Positive scale so item is right-side up and outside the model.
        // Negate Y to convert from MC model space (Y-down) to item space (Y-up).
        pose.scale(scale, -scale, scale);
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY,
                pose, buf, player.level(), player.getId());
        pose.popPose();
    }
}
