package com.tarkovinventory.compat.tacz;

import net.minecraft.world.entity.player.Player;

/**
 * Thread-local bridge so the {@code findAndExtractInventoryAmmo} mixin (which only
 * receives an IItemHandler) can know which player is reloading. The mixin captures
 * the player at the start of the player-context ammo methods and reads it here.
 *
 * IMPORTANT: this lives OUTSIDE the mixin package, because classes in a mixin
 * package may only be mixins and cannot be referenced directly by other code.
 */
public final class TaczRigContext {

    private TaczRigContext() {}

    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    public static void set(Player player) { CURRENT.set(player); }
    public static void clear() { CURRENT.remove(); }
    public static Player currentPlayer() { return CURRENT.get(); }
}
