package com.reis.telegraph.client;

import com.reis.telegraph.gui.TelegraphReadScreen;
import com.reis.telegraph.gui.TelegraphScreen;
import com.reis.telegraph.items.TelegraphMessageItem;
import com.reis.telegraph.network.packets.OpenGuiPacket;
import com.reis.telegraph.registration.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Client-only setup. Invoked via DistExecutor.safeRunWhenOn so the entire
 * class reference is stripped from the server bytecode by runtimedistcleaner.
 * This ensures no client-only Screen subclass appears in the constant pool of
 * any class that is loaded on the dedicated server.
 */
@OnlyIn(Dist.CLIENT)
public class TelegraphClientSetup {

    public static void init() {
        OpenGuiPacket.clientScreenOpener = (pos, channel, stationName, quality, targets) ->
                net.minecraft.client.Minecraft.getInstance()
                        .setScreen(new TelegraphScreen(pos, channel, stationName, quality, targets));

        TelegraphMessageItem.clientScreenOpener = stack ->
                net.minecraft.client.Minecraft.getInstance()
                        .setScreen(new TelegraphReadScreen(stack));

        FMLJavaModLoadingContext.get().getModEventBus().addListener(TelegraphClientSetup::registerEntityRenderers);
    }

    private static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DYNAMITE.get(), ThrownItemRenderer::new);
    }
}
