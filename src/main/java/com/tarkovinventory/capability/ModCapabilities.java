package com.tarkovinventory.capability;

import com.tarkovinventory.TarkovInventoryMod;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = TarkovInventoryMod.MOD_ID)
public class ModCapabilities {

    public static final Capability<IPlayerEquipment> PLAYER_EQUIPMENT =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation EQUIPMENT_KEY =
            new ResourceLocation(TarkovInventoryMod.MOD_ID, "player_equipment");

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.register(IPlayerEquipment.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<net.minecraft.world.entity.Entity> event) {
        if (!(event.getObject() instanceof Player)) return;

        PlayerEquipmentCapability impl = new PlayerEquipmentCapability();
        LazyOptional<IPlayerEquipment> optional = LazyOptional.of(() -> impl);

        event.addCapability(EQUIPMENT_KEY, new net.minecraftforge.common.capabilities.ICapabilitySerializable<net.minecraft.nbt.CompoundTag>() {
            @Override
            public @NotNull <T> LazyOptional<T> getCapability(
                    @NotNull Capability<T> cap, @Nullable Direction side) {
                return PLAYER_EQUIPMENT.orEmpty(cap, optional);
            }

            @Override
            public net.minecraft.nbt.CompoundTag serializeNBT() {
                return impl.serializeNBT();
            }

            @Override
            public void deserializeNBT(net.minecraft.nbt.CompoundTag tag) {
                impl.deserializeNBT(tag);
            }
        });
        event.addListener(optional::invalidate);
    }

    /** Convenience getter — returns empty optional if player or cap is missing. */
    public static LazyOptional<IPlayerEquipment> get(Player player) {
        return player.getCapability(PLAYER_EQUIPMENT);
    }
}
