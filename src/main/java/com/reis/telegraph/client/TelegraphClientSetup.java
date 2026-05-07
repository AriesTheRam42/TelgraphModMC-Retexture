package com.reis.telegraph.client;

import com.reis.telegraph.gui.TelegraphReadScreen;
import com.reis.telegraph.gui.TelegraphScreen;
import com.reis.telegraph.items.TelegraphMessageItem;
import com.reis.telegraph.network.packets.OpenGuiPacket;
import com.reis.telegraph.registration.ModBlocks;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only setup. Invoked via DistExecutor.safeRunWhenOn so the entire
 * class reference is stripped from the server bytecode by runtimedistcleaner.
 * This ensures no client-only Screen subclass appears in the constant pool of
 * any class that is loaded on the dedicated server.
 */
@OnlyIn(Dist.CLIENT)
public class TelegraphClientSetup {

    public static void init(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            OpenGuiPacket.clientScreenOpener = (pos, channel, stationName, quality) ->
                    net.minecraft.client.Minecraft.getInstance()
                            .setScreen(new TelegraphScreen(pos, channel, stationName, quality));

            TelegraphMessageItem.clientScreenOpener = stack ->
                    net.minecraft.client.Minecraft.getInstance()
                            .setScreen(new TelegraphReadScreen(stack));

            // Set render types for blocks with transparency/non-full models
            // This prevents "black boxes" or "missing models" look on some server configurations
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.CABLE_BLOCK.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.TELEGRAPH_POLE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.INSULATOR.get(), RenderType.cutout());
        });
    }
}
