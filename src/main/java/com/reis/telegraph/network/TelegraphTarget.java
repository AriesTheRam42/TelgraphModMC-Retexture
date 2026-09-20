package com.reis.telegraph.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * A single reachable telegraph machine as shown in the sender's recipient list.
 * Built server-side and sent to the client with OpenGuiPacket so the player can
 * pick exactly which stations a message goes to.
 */
public record TelegraphTarget(BlockPos pos, String stationName, int channel,
                              int distance, int quality, boolean selected) {

    /** Hard cap so a huge network can never produce an oversized packet. */
    public static final int MAX_TARGETS = 64;

    public static void encode(TelegraphTarget target, FriendlyByteBuf buf) {
        buf.writeBlockPos(target.pos);
        buf.writeUtf(target.stationName == null ? "" : target.stationName, 32);
        buf.writeVarInt(target.channel);
        buf.writeVarInt(target.distance);
        buf.writeVarInt(Math.max(0, target.quality));
        buf.writeBoolean(target.selected);
    }

    public static TelegraphTarget decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String station = buf.readUtf(32);
        int channel = buf.readVarInt();
        int distance = buf.readVarInt();
        int quality = buf.readVarInt();
        boolean selected = buf.readBoolean();
        return new TelegraphTarget(pos, station, channel, distance, quality, selected);
    }

    public static void encodeList(List<TelegraphTarget> targets, FriendlyByteBuf buf) {
        int count = Math.min(targets.size(), MAX_TARGETS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            encode(targets.get(i), buf);
        }
    }

    public static List<TelegraphTarget> decodeList(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_TARGETS);
        List<TelegraphTarget> targets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            targets.add(decode(buf));
        }
        return targets;
    }

    public static void encodePositions(List<BlockPos> positions, FriendlyByteBuf buf) {
        int count = Math.min(positions.size(), MAX_TARGETS);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeBlockPos(positions.get(i));
        }
    }

    public static List<BlockPos> decodePositions(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_TARGETS);
        List<BlockPos> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            positions.add(buf.readBlockPos());
        }
        return positions;
    }
}
