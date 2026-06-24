package com.tarkovinventory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tarkovinventory.capability.IPlayerEquipment;
import com.tarkovinventory.capability.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Renders custom Tarkov equipment slots visually on the player model:
 *
 *   EAR   → item rendered at/near the head (headset position)
 *   FACE  → item rendered on the front of the head (face cover)
 *   RIG   → item rendered on the torso (chest rig)
 *   KNEE  → item rendered on both shins (knee pads)
 *
 * Registered via EntityRenderersEvent.AddLayers in ClientSetup for both
 * "default" and "slim" player skin types.
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
            var itemRenderer = Minecraft.getInstance().getItemRenderer();
            var model        = getParentModel();

            // ── EAR / Headset ─────────────────────────────────────
            ItemStack ear = cap.getSlot(IPlayerEquipment.SLOT_EARPIECE);
            if (!ear.isEmpty()) {
                pose.pushPose();
                model.head.translateAndRotate(pose);
                // Offset slightly to the left and up so it sits on the ear
                pose.translate(-0.35D, -0.1D, 0.0D);
                pose.scale(0.5f, -0.5f, -0.5f);
                pose.translate(-0.5D, -0.5D, -0.5D);
                itemRenderer.renderStatic(ear, ItemDisplayContext.HEAD,
                        light, OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
                pose.popPose();
            }

            // ── FACE / Face cover ─────────────────────────────────
            ItemStack face = cap.getSlot(IPlayerEquipment.SLOT_FACE);
            if (!face.isEmpty()) {
                pose.pushPose();
                model.head.translateAndRotate(pose);
                // Centre in front of the face
                pose.translate(0.0D, 0.05D, -0.55D);
                pose.mulPose(Axis.XP.rotationDegrees(90f));
                pose.scale(0.5f, 0.5f, 0.5f);
                pose.translate(-0.5D, -0.5D, -0.5D);
                itemRenderer.renderStatic(face, ItemDisplayContext.HEAD,
                        light, OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
                pose.popPose();
            }

            // ── RIG / Chest rig ───────────────────────────────────
            ItemStack rig = cap.getSlot(IPlayerEquipment.SLOT_RIG);
            if (!rig.isEmpty()) {
                pose.pushPose();
                model.body.translateAndRotate(pose);
                pose.translate(0.0D, 0.0D, -0.3D);
                pose.scale(0.6f, -0.6f, 0.6f);
                pose.translate(-0.5D, -0.5D, -0.5D);
                itemRenderer.renderStatic(rig, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        light, OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
                pose.popPose();
            }

            // ── KNEE / Knee pads (both legs) ──────────────────────
            ItemStack knee = cap.getSlot(IPlayerEquipment.SLOT_KNEES);
            if (!knee.isEmpty()) {
                // Left leg
                pose.pushPose();
                model.leftLeg.translateAndRotate(pose);
                pose.translate(0.0D, 0.25D, -0.2D);
                pose.scale(0.4f, -0.4f, -0.4f);
                pose.translate(-0.5D, -0.5D, -0.5D);
                itemRenderer.renderStatic(knee, ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
                        light, OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
                pose.popPose();

                // Right leg
                pose.pushPose();
                model.rightLeg.translateAndRotate(pose);
                pose.translate(0.0D, 0.25D, -0.2D);
                pose.scale(0.4f, -0.4f, -0.4f);
                pose.translate(-0.5D, -0.5D, -0.5D);
                itemRenderer.renderStatic(knee, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                        light, OverlayTexture.NO_OVERLAY, pose, buffers, player.level(), player.getId());
                pose.popPose();
            }
        });
    }
}
