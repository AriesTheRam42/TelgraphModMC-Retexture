package com.reis.telegraph.network.packets;

import com.mojang.logging.LogUtils;
import com.reis.telegraph.blocks.TelegraphBlockEntity;
import com.reis.telegraph.network.TelegraphTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.List;
import java.util.function.Supplier;

/**
 * Persists the recipient selection the player made in the telegraph GUI, so the
 * routing survives closing the screen without sending anything.
 */
public class SetTargetsPacket {

    private static final Logger LOGGER = LogUtils.getLogger();

    private final BlockPos pos;
    private final List<BlockPos> targets;

    public SetTargetsPacket(BlockPos pos, List<BlockPos> targets) {
        this.pos = pos;
        this.targets = targets == null ? List.of() : targets;
    }

    public static void encode(SetTargetsPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        TelegraphTarget.encodePositions(pkt.targets, buf);
    }

    public static SetTargetsPacket decode(FriendlyByteBuf buf) {
        return new SetTargetsPacket(buf.readBlockPos(), TelegraphTarget.decodePositions(buf));
    }

    public static void handle(SetTargetsPacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            if (player.blockPosition().distSqr(pkt.pos) > 64.0) {
                LOGGER.debug("[Telegraph] SetTargetsPacket: player too far from {}", pkt.pos);
                return;
            }

            ServerLevel level = player.serverLevel();
            BlockEntity be = level.getBlockEntity(pkt.pos);
            if (!(be instanceof TelegraphBlockEntity tbe)) {
                LOGGER.debug("[Telegraph] SetTargetsPacket: no TelegraphBlockEntity at {}", pkt.pos);
                return;
            }

            tbe.setSelectedTargets(pkt.targets);
            LOGGER.debug("[Telegraph] SetTargetsPacket: {} routed {} to {} target(s)",
                    player.getName().getString(), pkt.pos, pkt.targets.size());
        });
        ctx.get().setPacketHandled(true);
    }
}
