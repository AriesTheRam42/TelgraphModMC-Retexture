package com.reis.telegraph.network;

import com.reis.telegraph.network.packets.OpenGuiPacket;
import com.reis.telegraph.network.packets.SendMessagePacket;
import com.reis.telegraph.network.packets.SetChannelPacket;
import com.reis.telegraph.network.packets.SetStationNamePacket;
import com.reis.telegraph.network.packets.SetTargetsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {

    // Bumped to 3 when the GUI gained per-recipient routing (OpenGuiPacket /
    // SendMessagePacket payloads changed, SetTargetsPacket added).
    private static final String PROTOCOL_VERSION = "3";
    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
                new ResourceLocation("telegraph", "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );

        int id = 0;
        CHANNEL.registerMessage(id++, SendMessagePacket.class,
                SendMessagePacket::encode,
                SendMessagePacket::decode,
                SendMessagePacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, OpenGuiPacket.class,
                OpenGuiPacket::encode,
                OpenGuiPacket::decode,
                OpenGuiPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(id++, SetChannelPacket.class,
                SetChannelPacket::encode,
                SetChannelPacket::decode,
                SetChannelPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, SetStationNamePacket.class,
                SetStationNamePacket::encode,
                SetStationNamePacket::decode,
                SetStationNamePacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(id++, SetTargetsPacket.class,
                SetTargetsPacket::encode,
                SetTargetsPacket::decode,
                SetTargetsPacket::handle,
                java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    public static void sendToClient(Object packet, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
