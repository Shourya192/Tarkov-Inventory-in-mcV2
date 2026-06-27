package com.tarkovinventory.network;

import com.tarkovinventory.container.TarkovInventoryMenu;
import com.tarkovinventory.inventory.RigContainer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → Server: perform a swap/pickup/place action on a rig or backpack slot.
 *
 * action:
 *   0 = PICKUP (take from slot into carried)
 *   1 = PLACE   (place carried into slot)
 *   2 = SWAP    (swap carried with slot)
 *
 * mode:
 *   0 = RIG
 *   1 = BACKPACK
 */
public class C2SRigActionPacket {

    private final int mode;   // 0=rig, 1=backpack
    private final int slot;
    private final int action; // 0=pickup, 1=place, 2=swap
    private final ItemStack carried; // the client's carried item (for place/swap)

    public C2SRigActionPacket(int mode, int slot, int action, ItemStack carried) {
        this.mode = mode;
        this.slot = slot;
        this.action = action;
        this.carried = carried;
    }

    public static void encode(C2SRigActionPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.mode);
        buf.writeVarInt(msg.slot);
        buf.writeVarInt(msg.action);
        buf.writeItem(msg.carried);
    }

    public static C2SRigActionPacket decode(FriendlyByteBuf buf) {
        return new C2SRigActionPacket(
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readItem()
        );
    }

    public static void handle(C2SRigActionPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!(player.containerMenu instanceof TarkovInventoryMenu menu)) return;

            RigContainer container = msg.mode == 0 ? menu.getRigContainer() : menu.getBackpackContainer();
            if (container == null) return;

            // Validate slot bounds
            if (msg.slot < 0 || msg.slot >= container.getContainerSize()) return;

            ItemStack slotItem = container.getItem(msg.slot);
            ItemStack clientCarried = msg.carried.copy();

            switch (msg.action) {
                case 0 -> { // PICKUP
                    if (slotItem.isEmpty()) return;
                    container.setItem(msg.slot, ItemStack.EMPTY);
                    player.containerMenu.setCarried(slotItem.copy());
                }
                case 1 -> { // PLACE
                    if (clientCarried.isEmpty()) return;
                    container.setItem(msg.slot, clientCarried);
                    player.containerMenu.setCarried(ItemStack.EMPTY);
                }
                case 2 -> { // SWAP
                    container.setItem(msg.slot, clientCarried);
                    player.containerMenu.setCarried(slotItem.copy());
                }
            }

            // Force sync to all tracking clients
            container.setChanged();
            player.containerMenu.broadcastChanges();
        });

        ctx.get().setPacketHandled(true);
    }
}
