package com.tarkovinventory.mixin;

import com.tarkovinventory.capability.RigAwareItemHandler;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Soft-dependency mixin into TaCZ's {@code AbstractGunItem}. When the gun searches
 * an inventory handler for ammo/magazines, swap in a rig-aware handler that also
 * exposes the equipped rig's contents — so magazines stored in the rig are found,
 * pulled, and empties returned. The reloading player comes from the thread-local
 * captured by {@link MixinTaczAmmoSearch}.
 *
 * priority = 500 so this @ModifyVariable applies before the TaCZ Magazines mod's
 * HEAD injection reads the handler argument.
 */
@Pseudo
@Mixin(targets = "com.tacz.guns.api.item.gun.AbstractGunItem", remap = false, priority = 500)
public class MixinTaczAmmoHandler {

    @ModifyVariable(
            method = {"findAndExtractInventoryAmmo", "findAndExtractInventoryAmmos"},
            at = @At("HEAD"),
            argsOnly = true,
            remap = false,
            require = 0)
    private IItemHandler tarkov$includeRig(IItemHandler original) {
        try {
            Player player = com.tarkovinventory.compat.tacz.TaczRigContext.currentPlayer();
            if (player != null) {
                return new RigAwareItemHandler(player, original);
            }
        } catch (Throwable ignored) {
            // Never break vanilla ammo search.
        }
        return original;
    }
}
