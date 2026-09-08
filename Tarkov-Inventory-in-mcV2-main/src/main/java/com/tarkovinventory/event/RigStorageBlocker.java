package com.tarkovinventory.event;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.config.ItemTypeConfig;
import com.tarkovinventory.inventory.BackpackSizes;
import com.tarkovinventory.inventory.RigSizes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Blocks the *native* storage of rig/backpack items (from other mods) so they can
 * ONLY be used through the Tarkov inventory system. Their own right-click / keybind
 * storage GUIs are prevented from opening, avoiding the dual-inventory problem.
 *
 * An item counts as a rig/backpack if it is registered in {@link RigSizes} /
 * {@link BackpackSizes}, or admin-tagged RIG / BACKPACK via the tagging UI.
 */
@Mod.EventBusSubscriber(modid = TarkovInventoryMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RigStorageBlocker {

    private RigStorageBlocker() {}

    /** True if the stack is a managed rig or backpack (built-in, override, or admin tag). */
    public static boolean isManagedGear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (RigSizes.isRegistered(stack) || BackpackSizes.isRegistered(stack)) return true;
        String t = ItemTypeConfig.getType(stack);
        return "RIG".equalsIgnoreCase(t) || "BACKPACK".equalsIgnoreCase(t);
    }

    /** Cancel right-click-in-air opening of a rig/backpack's native storage.
     *  Fires on BOTH sides so client-side-opened GUIs are also blocked. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isManagedGear(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Also catch the generic empty-hand / use entity path on both sides. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isManagedGear(event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Cancel right-click-on-block when holding a rig/backpack (some mods open via this). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isManagedGear(event.getItemStack())) {
            // Only block the item's own use, not block interaction generally — so we
            // cancel just the ITEM use result, allowing the block to still be used.
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
        }
    }

    /**
     * Catch keybind / other paths that open a rig/backpack's native container.
     * To avoid false-positives (e.g. opening a chest while holding a backpack),
     * we only act when the player is NOT currently targeting a block — i.e. the
     * menu was opened from an item action/keybind, not a world container.
     */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player == null) return;
        var menu = event.getContainer();
        if (menu == null) return;
        // Our own menu is always allowed.
        if (menu instanceof com.tarkovinventory.container.TarkovInventoryMenu) return;
        // Vanilla player inventory menu is allowed.
        if (menu instanceof net.minecraft.world.inventory.InventoryMenu) return;

        String menuClass = menu.getClass().getName().toLowerCase(java.util.Locale.ROOT);

        // Known rig/backpack storage menus from supported mods — block these
        // UNCONDITIONALLY, since these menu types only ever belong to a rig/backpack's
        // native storage, which the Tarkov system replaces entirely.
        //   Modern Mayhem:      net.tkg.ModernMayhem.server.GUI.GenericBackpackGUI
        //                       net.tkg.ModernMayhem.server.util.AbstractContainerMenuUtil
        //   Survivor's Arsenal: com.og.survivorsarsenal.common.item.backpack.BackpackMenu
        boolean knownGearMenu =
                menuClass.contains("genericbackpackgui")
             || menuClass.contains("abstractcontainermenuutil")
             || menuClass.contains("modernmayhem")
             || menuClass.contains("survivorsarsenal")
             || menuClass.contains("backpackmenu");
        if (knownGearMenu) {
            player.closeContainer();
            return;
        }

        // Heuristic for other mods: storage-looking menu + player carries managed gear.
        boolean looksLikeBackpackStorage =
                menuClass.contains("backpack") || menuClass.contains("rig")
             || menuClass.contains("sophisticated") || menuClass.contains("travel")
             || menuClass.contains("pouch") || menuClass.contains("satchel");
        if (looksLikeBackpackStorage && playerHasManagedGear(player)) {
            player.closeContainer();
            return;
        }

        // Fallback: if the player is looking at a block, the menu is almost certainly
        // a world container (chest, etc.) — leave it alone.
        net.minecraft.world.phys.HitResult hit = player.pick(5.0, 0.0f, false);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) return;

        if (playerHasManagedGear(player)) {
            player.closeContainer();
        }
    }

    /** True if the player holds or carries a managed rig/backpack anywhere. */
    private static boolean playerHasManagedGear(Player player) {
        if (isManagedGear(player.getMainHandItem()) || isManagedGear(player.getOffhandItem()))
            return true;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (isManagedGear(inv.getItem(i))) return true;
        }
        return false;
    }
}
