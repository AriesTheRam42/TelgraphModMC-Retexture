package com.reis.telegraph.network.packets;

import com.reis.telegraph.gui.ScreenOpenCallback;
import com.reis.telegraph.network.TelegraphTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

public class OpenGuiPacket {

    /**
     * Set on the client by TelegraphClientSetup.init() — null on the dedicated server.
     * Using a functional interface from common code keeps TelegraphScreen out of this
     * class's constant pool, preventing NoClassDefFoundError on Mohist / dedicated servers.
     */
    public static ScreenOpenCallback clientScreenOpener = null;

    private final BlockPos pos;
    private final int currentChannel;
    private final String stationName;
    private final int lastSignalQuality;
    private final List<TelegraphTarget> targets;

    public OpenGuiPacket(BlockPos pos, int currentChannel, String stationName,
                         int lastSignalQuality, List<TelegraphTarget> targets) {
        this.pos = pos;
        this.currentChannel = currentChannel;
        this.stationName = stationName == null ? "" : stationName;
        this.lastSignalQuality = lastSignalQuality;
        this.targets = targets == null ? List.of() : targets;
    }

    public static void encode(OpenGuiPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeVarInt(pkt.currentChannel);
        buf.writeUtf(pkt.stationName, 32);
        buf.writeVarInt(pkt.lastSignalQuality);
        TelegraphTarget.encodeList(pkt.targets, buf);
    }

    public static OpenGuiPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int channel = buf.readVarInt();
        String stationName = buf.readUtf(32);
        int quality = buf.readVarInt();
        List<TelegraphTarget> targets = TelegraphTarget.decodeList(buf);
        return new OpenGuiPacket(pos, channel, stationName, quality, targets);
    }

    public static void handle(OpenGuiPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (clientScreenOpener != null) {
                clientScreenOpener.open(pkt.pos, pkt.currentChannel, pkt.stationName,
                        pkt.lastSignalQuality, pkt.targets);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
