package com.reis.telegraph;

import com.reis.telegraph.client.TelegraphClientSetup;
import com.reis.telegraph.commands.TelegraphCommand;
import com.reis.telegraph.config.TelegraphConfig;
import com.reis.telegraph.network.PacketHandler;
import com.reis.telegraph.registration.*;
import com.reis.telegraph.system.MessageDeliverySystem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("telegraph")
public class TelegraphMod {

    public TelegraphMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TelegraphConfig.SPEC);

        // safeRunWhenOn is processed by runtimedistcleaner — the TelegraphClientSetup
        // reference is physically stripped from the server bytecode, preventing
        // NoClassDefFoundError on Mohist and other dedicated server implementations.
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> TelegraphClientSetup::init);

        ModBlocks.BLOCKS.register(modEventBus);
        ModBlocks.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITIES.register(modEventBus);
        ModCreativeTab.CREATIVE_TABS.register(modEventBus);

        PacketHandler.register();

        IEventBus forgeBus = MinecraftForge.EVENT_BUS;
        forgeBus.addListener(this::onRegisterCommands);
        forgeBus.register(MessageDeliverySystem.class);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        TelegraphCommand.register(event.getDispatcher());
    }
}
