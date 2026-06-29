package com.tarkovinventory.mixin;

import com.tarkovinventory.capability.RigAwareItemHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Soft-dependency mixin into TaCZ's {@code AbstractGunItem}. When a gun searches
 * the player's inventory handler for ammo/magazines, we swap in a handler that
 * ALSO exposes the equipped rig's contents — so magazines stored in the rig are
 * found, pulled, and (empty mags) returned, exactly like inventory ammo.
 *
 * Uses {@link Pseudo} so the mod compiles and loads fine even when TaCZ is absent
 * (the mixin simply doesn't apply). {@code defaultRequire: 0} in the mixin config
 * ensures a missing target never crashes the game.
 *
 * Target method (TaCZ 1.1.8):
 *   int findAndExtractInventoryAmmo(IItemHandler handler, ItemStack gun, int need)
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.api.item.gun.AbstractGunItem", remap = false)
public class MixinTaczAmmoSearch {

    /**
     * Capture the reloading player at the start of the player-context ammo check,
     * so the handler-only findAndExtractInventoryAmmo mixin can build a rig handler.
     */
    @Inject(
            method = "hasInventoryAmmo",
            at = @At("HEAD"),
            remap = false,
            require = 0)
    private void tarkov$capturePlayer(LivingEntity entity, net.minecraft.world.item.ItemStack gun,
                                      boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof Player p) {
            TarkovRigHandlerContext.set(p);
        }
    }

    @Inject(
            method = "hasInventoryAmmo",
            at = @At("RETURN"),
            remap = false,
            require = 0)
    private void tarkov$clearPlayer(LivingEntity entity, net.minecraft.world.item.ItemStack gun,
                                    boolean simulate, CallbackInfoReturnable<Boolean> cir) {
        TarkovRigHandlerContext.clear();
    }

    /**
     * Replace the IItemHandler argument with a rig-aware handler that includes the
     * player's equipped rig.
     */
    @ModifyVariable(
            method = "findAndExtractInventoryAmmo",
            at = @At("HEAD"),
            argsOnly = true,
            remap = false,
            require = 0)
    private IItemHandler tarkov$includeRig(IItemHandler original) {
        try {
            Player player = TarkovRigHandlerContext.currentPlayer();
            if (player != null) {
                return new RigAwareItemHandler(player, original);
            }
        } catch (Throwable ignored) {
            // Never let this break vanilla ammo search.
        }
        return original;
    }
}
