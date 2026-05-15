package com.reis.telegraph.registration;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "telegraph");

    public static final RegistryObject<CreativeModeTab> TELEGRAPH_TAB =
            CREATIVE_TABS.register("telegraph_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.telegraph"))
                            .icon(() -> new ItemStack(ModBlocks.TELEGRAPH_MACHINE.get()))
                            .displayItems((params, output) -> {
                                output.accept(ModBlocks.TELEGRAPH_MACHINE.get());
                                output.accept(ModBlocks.CABLE_BLOCK.get());
                                output.accept(ModItems.TELEGRAM.get());
                                output.accept(ModItems.CABLE_CUTTER.get());
                                output.accept(ModItems.DYNAMITE.get());
                                output.accept(ModBlocks.RELAY_STATION.get());
                                output.accept(ModBlocks.TELEGRAPH_POLE.get());
                                output.accept(ModBlocks.INSULATOR.get());
                            })
                            .build());
}
