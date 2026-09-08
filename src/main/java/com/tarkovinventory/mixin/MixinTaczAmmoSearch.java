package com.tarkovinventory.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Soft-dependency mixin into TaCZ's reload controller {@code LivingEntityReload}.
 * The reload flow calls {@code useInventoryAmmo} to pull ammo/magazines from the
 * player's inventory handler. We capture the reloading entity (the {@code shooter}
 * field) into a thread-local so the rig-aware item handler can be supplied to the
 * ammo search (see {@link MixinTaczAmmoHandler}).
 *
 * Uses {@link Pseudo} + require=0 so a missing/changed target never crashes.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.entity.shooter.LivingEntityReload", remap = false)
public class MixinTaczAmmoSearch {

    @Shadow(remap = false)
    private LivingEntity shooter;

    @Inject(method = "useInventoryAmmo", at = @At("HEAD"), remap = false, require = 0)
    private void tarkov$captureShooter(net.minecraft.world.item.ItemStack gun,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (shooter instanceof Player p) {
            com.tarkovinventory.compat.tacz.TaczRigContext.set(p);
        }
    }

    @Inject(method = "useInventoryAmmo", at = @At("RETURN"), remap = false, require = 0)
    private void tarkov$clearShooter(net.minecraft.world.item.ItemStack gun,
                                     CallbackInfoReturnable<Boolean> cir) {
        com.tarkovinventory.compat.tacz.TaczRigContext.clear();
    }
}
