package com.tarkovinventory.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.tarkovinventory.capability.IPlayerEquipment;
import com.tarkovinventory.capability.ModCapabilities;
import com.tarkovinventory.compat.CuriosCompat;
import com.tarkovinventory.container.TarkovInventoryMenu;
import com.tarkovinventory.inventory.BackpackSizes;
import com.tarkovinventory.inventory.GridInventory;
import com.tarkovinventory.inventory.RigSizes;
import com.tarkovinventory.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public final class TarkovCommand {

    private TarkovCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // Subcommands:
        //   /ti                        — open inventory screen
        //   /ti backpack               — give yourself a Tactical Backpack item
        //   /ti curiosinfo             — list Curios slot IDs + items
        //   /ti gridinfo               — show equipped backpack ID and active grid size
        //   /ti setsize <cols> <rows>  — set grid size for equipped backpack (session only)
        //
        // Registered under both "ti" and "tarkovinventory" explicitly (no redirect,
        // which is unreliable in Forge 1.20.1 and can break the whole tree).

        dispatcher.register(
            Commands.literal("setsize")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("backpack")
                    .then(Commands.argument("columns", IntegerArgumentType.integer(1, GridInventory.MAX_COLS))
                        .then(Commands.argument("rows", IntegerArgumentType.integer(1, GridInventory.MAX_ROWS))
                            .executes(ctx -> setBackpackSize(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "columns"),
                                IntegerArgumentType.getInteger(ctx, "rows"))))))
                .then(Commands.literal("rig")
                    .then(Commands.argument("columns", IntegerArgumentType.integer(1, GridInventory.MAX_COLS))
                        .then(Commands.argument("rows", IntegerArgumentType.integer(1, GridInventory.MAX_ROWS))
                            .executes(ctx -> setRigSize(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "columns"),
                                IntegerArgumentType.getInteger(ctx, "rows"))))))
        );

        for (String alias : new String[]{"ti", "tarkovinventory"}) {
            dispatcher.register(
                Commands.literal(alias)
                    .executes(ctx -> openInventory(ctx.getSource()))
                    .then(Commands.literal("backpack")
                        .executes(ctx -> giveBackpack(ctx.getSource())))
                    .then(Commands.literal("curiosinfo")
                        .executes(ctx -> printCuriosInfo(ctx.getSource())))
                    .then(Commands.literal("gridinfo")
                        .executes(ctx -> printGridInfo(ctx.getSource())))
                    .then(Commands.literal("setsize")
                        .then(Commands.argument("cols", IntegerArgumentType.integer(1, GridInventory.MAX_COLS))
                            .then(Commands.argument("rows", IntegerArgumentType.integer(1, GridInventory.MAX_ROWS))
                                .executes(ctx -> setBackpackSize(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "cols"),
                                    IntegerArgumentType.getInteger(ctx, "rows"))))))
                    .then(Commands.literal("setrigsize")
                        .then(Commands.argument("cols", IntegerArgumentType.integer(1, GridInventory.MAX_COLS))
                            .then(Commands.argument("rows", IntegerArgumentType.integer(1, GridInventory.MAX_ROWS))
                                .executes(ctx -> setRigSize(ctx.getSource(),
                                    IntegerArgumentType.getInteger(ctx, "cols"),
                                    IntegerArgumentType.getInteger(ctx, "rows"))))))
                    .then(Commands.literal("riginfo")
                        .executes(ctx -> printRigInfo(ctx.getSource())))
            );
        }
    }

    private static int openInventory(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            NetworkHooks.openScreen(player, new MenuProvider() {
                @Override
                public @NotNull Component getDisplayName() {
                    return Component.literal("Tarkov Inventory");
                }
                @Override
                public @NotNull AbstractContainerMenu createMenu(
                        int windowId, @NotNull Inventory inv, @NotNull Player p) {
                    return new TarkovInventoryMenu(windowId, inv, 0);
                }
            }, buf -> TarkovInventoryMenu.writeDimensions(buf, player));
        } catch (Exception ignored) {}
        return 1;
    }

    private static int giveBackpack(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ItemStack backpack = new ItemStack(ModItems.TACTICAL_BACKPACK.get());
            player.getInventory().add(backpack);
            player.displayClientMessage(
                Component.literal("§aGave you a Tactical Backpack! Equip it in the ON BACK slot."),
                false
            );
        } catch (Exception ignored) {}
        return 1;
    }

    private static int printCuriosInfo(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();

            // 1. Is Curios in the mod list?
            boolean isLoaded = CuriosCompat.isLoaded();
            player.displayClientMessage(Component.literal(
                "§6Curios loaded: §" + (isLoaded ? "a" : "c") + isLoaded), false);

            if (!isLoaded) {
                // Show any mod whose ID contains "curi" so we can spot alternate modIds
                player.displayClientMessage(Component.literal("§eMods with 'curi' in ID:"), false);
                net.minecraftforge.fml.ModList.get().getMods().forEach(info -> {
                    if (info.getModId().toLowerCase().contains("curi"))
                        player.displayClientMessage(Component.literal(
                            "  §a" + info.getModId() + " §7— " + info.getDisplayName()), false);
                });
                player.displayClientMessage(Component.literal(
                    "§7If Curios has a different modId, update isLoaded() in CuriosCompat.java"), false);
                return 1;
            }

            // 2. Can we find the CuriosApi class?
            boolean classFound = false;
            try { Class.forName("top.theillusivec4.curios.api.CuriosApi"); classFound = true; }
            catch (ClassNotFoundException ignored) {}
            player.displayClientMessage(Component.literal(
                "§6CuriosApi class: §" + (classFound ? "a" : "c") + (classFound ? "found" : "NOT FOUND")), false);

            // 3. Can we get a handler?
            Object handler = CuriosCompat.getHandler(player);
            player.displayClientMessage(Component.literal(
                "§6Handler: §" + (handler != null ? "a" : "c") +
                (handler != null ? handler.getClass().getSimpleName() : "null — all reflection patterns failed")), false);

            if (handler == null) {
                player.displayClientMessage(Component.literal(
                    "§7Check your Curios version. Expected: 5.3.0+ for Forge 1.20.1"), false);
                return 1;
            }

            // 4. List all slots
            List<CuriosCompat.CuriosSlotEntry> slots = CuriosCompat.getEquippedSlots(player);
            if (slots.isEmpty()) {
                // Show all slot IDs even if empty
                try {
                    Map<?, ?> curios = (Map<?, ?>) handler.getClass().getMethod("getCurios").invoke(handler);
                    player.displayClientMessage(Component.literal("§6All Curios slot IDs:"), false);
                    curios.keySet().forEach(k -> player.displayClientMessage(
                        Component.literal("  §e\"" + k + "\""), false));
                } catch (Throwable t) {
                    player.displayClientMessage(Component.literal("§cCould not list slots: " + t.getMessage()), false);
                }
            } else {
                player.displayClientMessage(Component.literal("§6=== Curios Slots ==="), false);
                for (CuriosCompat.CuriosSlotEntry entry : slots) {
                    String item = entry.stack().isEmpty() ? "§8(empty)" : "§a" + entry.stack().getHoverName().getString();
                    player.displayClientMessage(Component.literal(
                        "  §e\"" + entry.slotId() + "\"§7[" + entry.index() + "] → " + item), false);
                }
            }
        } catch (Exception e) {
            try { source.getPlayerOrException().displayClientMessage(
                Component.literal("§cError: " + e.getClass().getSimpleName() + ": " + e.getMessage()), false);
            } catch (Exception ignored) {}
        }
        return 1;
    }

    /**
     * /ti gridinfo — shows the equipped backpack's item ID and active grid size.
     * Useful for finding the item ID to add to BackpackSizes.java.
     */
    private static int printGridInfo(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ItemStack backpack = getBackSlotItem(player);

            player.displayClientMessage(
                Component.literal("§6=== Tarkov Grid Info ==="), false);

            if (backpack.isEmpty()) {
                player.displayClientMessage(
                    Component.literal("§eNo backpack equipped in the ON BACK / Curios 'back' slot."), false);
                player.displayClientMessage(
                    Component.literal("§7Equip something in that slot, then run §f/ti gridinfo§7 again."), false);
                return 1;
            }

            String itemId      = BackpackSizes.getItemId(backpack);
            int    cols        = BackpackSizes.getCols(backpack);
            int    rows        = BackpackSizes.getRows(backpack);
            boolean registered = BackpackSizes.isRegistered(backpack);

            player.displayClientMessage(
                Component.literal("§7Item ID: §f" + itemId), false);
            player.displayClientMessage(
                Component.literal("§7Name: §f" + backpack.getHoverName().getString()), false);
            player.displayClientMessage(
                Component.literal("§7Grid: §a" + cols + "§7×§a" + rows
                    + (registered ? " §7(in BackpackSizes registry)" : " §e(using default 6×6 — not registered)")),
                false);

            if (!registered) {
                player.displayClientMessage(
                    Component.literal("§7Test a size now:  §f/ti setsize <cols> <rows>"), false);
                player.displayClientMessage(
                    Component.literal("§7To make it permanent, add to BackpackSizes.java:"), false);
                player.displayClientMessage(
                    Component.literal("§f  register(\"" + itemId + "\", cols, rows);"), false);
            }

        } catch (Exception e) {
            try {
                source.getPlayerOrException().displayClientMessage(
                    Component.literal("§cError: " + e.getClass().getSimpleName() + ": " + e.getMessage()), false);
            } catch (Exception ignored) {}
        }
        return 1;
    }

    /**
     * /ti setsize <cols> <rows> — registers the equipped backpack at the given
     * grid size for this session. Resets when the world is reloaded.
     * Use /ti gridinfo afterwards to confirm, then copy the line into BackpackSizes.java.
     */
    private static int setBackpackSize(CommandSourceStack source, int cols, int rows) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ItemStack backpack = getBackSlotItem(player);

            if (backpack.isEmpty()) {
                player.displayClientMessage(
                    Component.literal("§cNo backpack in the ON BACK / Curios 'back' slot. Equip one first."), false);
                return 0;
            }

            String itemId = BackpackSizes.getItemId(backpack);
            if (itemId.equals("unknown")) {
                player.displayClientMessage(
                    Component.literal("§cCouldn't get an item ID for that backpack."), false);
                return 0;
            }

            BackpackSizes.register(itemId, cols, rows);
            ModCapabilities.get(player).ifPresent(cap -> cap.getGridInventory().setActiveDimensions(cols, rows));

            // If the player has the Tarkov inventory open, notify it to re-sync
            if (player.containerMenu instanceof TarkovInventoryMenu tarkovMenu) {
                tarkovMenu.syncRigToClient(1); // mode 1 = backpack
            }

            player.displayClientMessage(
                Component.literal("§aRegistered §f\"" + itemId + "\"§a → §f"
                    + cols + "§a×§f" + rows + "§a grid for this session."), false);
            player.displayClientMessage(
                Component.literal("§7Reopen the inventory (§f/ti§7) to see it."), false);
            player.displayClientMessage(
                Component.literal("§7To make it permanent, add to BackpackSizes.java:"), false);
            player.displayClientMessage(
                Component.literal("§f  register(\"" + itemId + "\", " + cols + ", " + rows + ");"), false);

        } catch (Exception e) {
            try {
                source.getPlayerOrException().displayClientMessage(
                    Component.literal("§cError: " + e.getClass().getSimpleName() + ": " + e.getMessage()), false);
            } catch (Exception ignored) {}
        }
        return 1;
    }

    /**
     * Returns the item in the player's ON BACK slot.
     * Checks Curios 'back' slot first (via CuriosCompat, which handles all
     * known API versions), then falls back to the capability SLOT_ON_BACK.
     */
    private static ItemStack getBackSlotItem(ServerPlayer player) {
        if (CuriosCompat.isLoaded()) {
            ItemStack s = CuriosCompat.getSlotItem(player, "back", 0);
            if (!s.isEmpty()) return s;
        }
        return ModCapabilities.get(player)
            .map(cap -> cap.getSlot(IPlayerEquipment.SLOT_ON_BACK))
            .orElse(ItemStack.EMPTY);
    }

    /**
     * Returns the item in the player's rig slot.
     * Checks Curios 'body' slot first, then falls back to vanilla CHEST.
     */
    private static ItemStack getRigSlotItem(ServerPlayer player) {
        if (CuriosCompat.isLoaded()) {
            ItemStack s = CuriosCompat.getSlotItem(player, "body", 0);
            if (!s.isEmpty()) return s;
        }
        return player.getItemBySlot(EquipmentSlot.CHEST);
    }

    /**
     * /ti riginfo — shows the equipped rig's item ID and active rig grid size.
     */
    private static int printRigInfo(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ItemStack rig = getRigSlotItem(player);

            player.displayClientMessage(
                Component.literal("§6=== Tarkov Rig Info ==="), false);

            if (rig.isEmpty()) {
                player.displayClientMessage(
                    Component.literal("§eNo rig equipped in the Curios 'body' / armor CHEST slot."), false);
                player.displayClientMessage(
                    Component.literal("§7Equip a rig, then run §f/ti riginfo§7 again."), false);
                return 1;
            }

            String itemId = RigSizes.getItemId(rig);
            int cols = RigSizes.getCols(rig);
            int rows = RigSizes.getRows(rig);
            boolean registered = RigSizes.isRegistered(rig);

            player.displayClientMessage(
                Component.literal("§7Item ID: §f" + itemId), false);
            player.displayClientMessage(
                Component.literal("§7Name: §f" + rig.getHoverName().getString()), false);
            player.displayClientMessage(
                Component.literal("§7Rig Grid: §a" + cols + "§7×§a" + rows
                    + (registered ? " §7(in RigSizes registry)" : " §e(using default 3×3 — not registered)")),
                false);

            if (!registered) {
                player.displayClientMessage(
                    Component.literal("§7Test a size now:  §f/ti setrigsize <cols> <rows>"), false);
                player.displayClientMessage(
                    Component.literal("§7To make it permanent, add to RigSizes.java:"), false);
                player.displayClientMessage(
                    Component.literal("§f  register(\"" + itemId + "\", cols, rows);"), false);
            }

        } catch (Exception e) {
            try {
                source.getPlayerOrException().displayClientMessage(
                    Component.literal("§cError: " + e.getClass().getSimpleName() + ": " + e.getMessage()), false);
            } catch (Exception ignored) {}
        }
        return 1;
    }

    /**
     * /ti setrigsize <cols> <rows> — registers the equipped rig at the given
     * grid size for this session and resizes its custom RigInventory.
     * Resets when the world is reloaded.
     */
    private static int setRigSize(CommandSourceStack source, int cols, int rows) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ItemStack rig = getRigSlotItem(player);

            if (rig.isEmpty()) {
                player.displayClientMessage(
                    Component.literal("§cNo rig in the Curios 'body' / armor CHEST slot. Equip one first."), false);
                return 0;
            }

            String itemId = RigSizes.getItemId(rig);
            if (itemId.equals("(empty)") || itemId.equals("unknown")) {
                player.displayClientMessage(
                    Component.literal("§cCouldn't get an item ID for that rig."), false);
                return 0;
            }

            // Register the size for this session
            RigSizes.register(itemId, cols, rows);

            // Resize the existing custom RigInventory stored in the rig's NBT
            net.minecraft.nbt.CompoundTag tag = rig.getOrCreateTag();
            com.tarkovinventory.inventory.RigInventory rigInv;
            if (tag.contains("TarkovRigInventory")) {
                rigInv = com.tarkovinventory.inventory.RigInventory.unwrapFromNBT(tag);
            } else {
                rigInv = new com.tarkovinventory.inventory.RigInventory(cols, rows);
            }
            rigInv.setSize(cols, rows);
            tag.put("TarkovRigInventory", rigInv.serializeNBT());

            // Re-set rig in slot to sync NBT to client
            if (CuriosCompat.isLoaded() && !CuriosCompat.getSlotItem(player, "body", 0).isEmpty()) {
                CuriosCompat.setSlot(player, "body", 0, rig);
            } else {
                player.setItemSlot(EquipmentSlot.CHEST, rig);
            }

            // If the player has the Tarkov inventory open, notify it to re-sync
            if (player.containerMenu instanceof TarkovInventoryMenu tarkovMenu) {
                tarkovMenu.syncRigToClient(0); // mode 0 = rig
            }

            player.displayClientMessage(
                Component.literal("§aRegistered rig §f\"" + itemId + "\"§a → §f"
                    + cols + "§a×§f" + rows + "§a grid for this session."), false);
            player.displayClientMessage(
                Component.literal("§7Reopen the inventory (§f/ti§7) to see it."), false);
            player.displayClientMessage(
                Component.literal("§7To make it permanent, add to RigSizes.java:"), false);
            player.displayClientMessage(
                Component.literal("§f  register(\"" + itemId + "\", " + cols + ", " + rows + ");"), false);

        } catch (Exception e) {
            try {
                source.getPlayerOrException().displayClientMessage(
                    Component.literal("§cError: " + e.getClass().getSimpleName() + ": " + e.getMessage()), false);
            } catch (Exception ignored) {}
        }
        return 1;
    }
}
