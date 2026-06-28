package com.tarkovinventory.client.screen.modules;

import com.tarkovinventory.network.C2SRigPlacePacket;
import com.tarkovinventory.network.C2SRigSlotPacket;
import com.tarkovinventory.network.ModNetwork;
import net.minecraft.world.item.ItemStack;

/**
 * Central client → server action bridge
 */
public final class InventoryActionRouter {

    private InventoryActionRouter() {}

    public static void takeFromRig(int slot) {
        ModNetwork.CHANNEL.sendToServer(new C2SRigSlotPacket(slot));
    }

    public static void placeIntoRig(int slot, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        ModNetwork.CHANNEL.sendToServer(
                new C2SRigPlacePacket(slot, stack.copy())
        );
    }
}
