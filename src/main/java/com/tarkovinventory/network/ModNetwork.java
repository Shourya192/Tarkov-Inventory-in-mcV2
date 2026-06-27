package com.tarkovinventory.network;

import com.tarkovinventory.TarkovInventoryMod;
import com.tarkovinventory.network.S2CLootSyncPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TarkovInventoryMod.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private static int id = 0;

    public static void register() {

        CHANNEL.registerMessage(id++, C2SOpenTarkovPacket.class,
                C2SOpenTarkovPacket::encode, C2SOpenTarkovPacket::decode, C2SOpenTarkovPacket::handle);

        CHANNEL.registerMessage(id++, S2COpenTarkovPacket.class,
                S2COpenTarkovPacket::encode, S2COpenTarkovPacket::decode, S2COpenTarkovPacket::handle);

        CHANNEL.registerMessage(id++, C2SPickupItemPacket.class,
                C2SPickupItemPacket::encode, C2SPickupItemPacket::decode, C2SPickupItemPacket::handle);

        CHANNEL.registerMessage(id++, C2SLootAllPacket.class,
                C2SLootAllPacket::encode, C2SLootAllPacket::decode, C2SLootAllPacket::handle);

        CHANNEL.registerMessage(id++, C2STakeFromCorpsePacket.class,
                C2STakeFromCorpsePacket::encode, C2STakeFromCorpsePacket::decode, C2STakeFromCorpsePacket::handle);

        CHANNEL.registerMessage(id++, S2CCorpseContentsPacket.class,
                S2CCorpseContentsPacket::encode, S2CCorpseContentsPacket::decode, S2CCorpseContentsPacket::handle);

        CHANNEL.registerMessage(id++, C2SRigSlotPacket.class,
                C2SRigSlotPacket::encode, C2SRigSlotPacket::decode, C2SRigSlotPacket::handle);

        CHANNEL.registerMessage(id++, C2SRigPlacePacket.class,
                C2SRigPlacePacket::encode, C2SRigPlacePacket::decode, C2SRigPlacePacket::handle);

        // ── New unified packets ─────────────────────────────────────
        CHANNEL.registerMessage(id++, C2SRigActionPacket.class,
                C2SRigActionPacket::encode, C2SRigActionPacket::decode, C2SRigActionPacket::handle);

        CHANNEL.registerMessage(id++, S2CRigSyncPacket.class,
                S2CRigSyncPacket::encode, S2CRigSyncPacket::decode, S2CRigSyncPacket::handle);

        CHANNEL.registerMessage(id++, C2STakeFromContainerPacket.class,
                C2STakeFromContainerPacket::encode,
                C2STakeFromContainerPacket::decode,
                C2STakeFromContainerPacket::handle);

        CHANNEL.registerMessage(id++, S2CEquipmentSyncPacket.class,
                S2CEquipmentSyncPacket::encode,
                S2CEquipmentSyncPacket::decode,
                S2CEquipmentSyncPacket::handle);
        CHANNEL.registerMessage(id++, S2CLootSyncPacket.class,
                S2CLootSyncPacket::encode,
                S2CLootSyncPacket::decode,
                S2CLootSyncPacket::handle);
    }
}
