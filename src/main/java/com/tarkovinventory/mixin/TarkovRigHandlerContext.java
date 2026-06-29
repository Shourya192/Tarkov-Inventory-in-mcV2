package com.tarkovinventory.mixin;

import net.minecraft.world.entity.player.Player;

/**
 * Thread-local bridge so the {@code findAndExtractInventoryAmmo} mixin (which only
 * receives an IItemHandler) can know which player is reloading. A second injection
 * captures the player at the start of the player-context reload/ammo methods.
 */
public final class TarkovRigHandlerContext {

    private TarkovRigHandlerContext() {}

    private static final ThreadLocal<Player> CURRENT = new ThreadLocal<>();

    public static void set(Player player) { CURRENT.set(player); }
    public static void clear() { CURRENT.remove(); }
    public static Player currentPlayer() { return CURRENT.get(); }
}
