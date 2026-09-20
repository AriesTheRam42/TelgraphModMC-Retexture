package com.reis.telegraph.network.packets;

import com.mojang.logging.LogUtils;
import com.reis.telegraph.blocks.CableBlock;
import com.reis.telegraph.blocks.InsulatorBlock;
import com.reis.telegraph.blocks.RelayStationBlock;
import com.reis.telegraph.blocks.TelegraphBlockEntity;
import com.reis.telegraph.blocks.TelegraphPoleBlock;
import com.reis.telegraph.config.TelegraphConfig;
import com.reis.telegraph.network.TelegraphTarget;
import com.reis.telegraph.registration.ModSounds;
import com.reis.telegraph.system.MessageDeliverySystem;
import com.reis.telegraph.system.TelegraphRouting;
import com.reis.telegraph.system.TelegraphPaperSupply;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

public class SendMessagePacket {

    private static final Logger LOGGER = LogUtils.getLogger();
    static final Map<UUID, Long> COOLDOWNS = new HashMap<>(); // package-visible for cleanup
    private static final long COOLDOWN_TICKS = 600L; // 30 seconds

    /** Called by MessageDeliverySystem on server stop to reset per-player cooldown state. */
    public static void clearCooldowns() {
        COOLDOWNS.clear();
    }

    private final BlockPos pos;
    private final String message;
    private final int channel;
    private final List<BlockPos> targets;

    public SendMessagePacket(BlockPos pos, String message, int channel, List<BlockPos> targets) {
        this.pos = pos;
        this.message = message;
        this.channel = channel;
        this.targets = targets == null ? List.of() : targets;
    }

    public static void encode(SendMessagePacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos);
        buf.writeUtf(pkt.message, 256);
        buf.writeVarInt(pkt.channel);
        TelegraphTarget.encodePositions(pkt.targets, buf);
    }

    public static SendMessagePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        String message = buf.readUtf(256);
        int channel = buf.readVarInt();
        List<BlockPos> targets = TelegraphTarget.decodePositions(buf);
        return new SendMessagePacket(pos, message, channel, targets);
    }

    public static void handle(SendMessagePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            LOGGER.debug("[Telegraph] SendMessagePacket received: player={} pos={} channel={} msg='{}'",
                    player.getName().getString(), pkt.pos, pkt.channel, pkt.message);

            // Range check
            if (player.blockPosition().distSqr(pkt.pos) > 64.0) {
                LOGGER.debug("[Telegraph] SendMessagePacket: player too far from {}", pkt.pos);
                return;
            }

            // Validate inputs
            if (pkt.message.isBlank() || pkt.message.length() > 256) {
                LOGGER.debug("[Telegraph] SendMessagePacket: invalid message length");
                return;
            }
            if (pkt.channel < 0 || pkt.channel > 99) {
                LOGGER.debug("[Telegraph] SendMessagePacket: invalid channel {}", pkt.channel);
                return;
            }

            ServerLevel level = player.serverLevel();
            long currentTick = level.getGameTime();

            // Cooldown check
            UUID uuid = player.getUUID();
            Long lastSend = COOLDOWNS.get(uuid);
            if (lastSend != null && currentTick - lastSend < COOLDOWN_TICKS) {
                long secsLeft = (COOLDOWN_TICKS - (currentTick - lastSend)) / 20L;
                LOGGER.debug("[Telegraph] SendMessagePacket: cooldown active for {}, {}s remaining",
                        player.getName().getString(), secsLeft);
                player.sendSystemMessage(Component.translatable("message.telegraph.cooldown", secsLeft));
                return;
            }

            // Validate block entity
            BlockEntity be = level.getBlockEntity(pkt.pos);
            if (!(be instanceof TelegraphBlockEntity tbe)) {
                LOGGER.debug("[Telegraph] SendMessagePacket: no TelegraphBlockEntity at {}", pkt.pos);
                return;
            }

            if (!TelegraphPaperSupply.hasPaper(level, pkt.pos)) {
                player.sendSystemMessage(Component.translatable("message.telegraph.no_paper"));
                return;
            }

            // Check that the machine has at least one cable/relay/pole connected
            boolean hasCable = false;
            for (Direction dir : Direction.values()) {
                var adj = level.getBlockState(pkt.pos.relative(dir)).getBlock();
                if (adj instanceof CableBlock || adj instanceof RelayStationBlock
                 || adj instanceof TelegraphPoleBlock || adj instanceof InsulatorBlock) {
                    hasCable = true;
                    break;
                }
            }
            if (!hasCable) {
                LOGGER.debug("[Telegraph] SendMessagePacket: no cable adjacent to {}", pkt.pos);
                player.sendSystemMessage(Component.literal(
                        "You did not create a telegraph line, therefore the message could not be delivered."));
                return;
            }

            // Update the machine's channel
            tbe.setChannel(pkt.channel);

            // Remember the routing the player picked, so it survives closing the GUI
            Set<BlockPos> requested = new HashSet<>(pkt.targets);
            tbe.setSelectedTargets(requested);

            List<TelegraphTarget> reachable = TelegraphRouting.collectTargets(level, pkt.pos, tbe);
            if (reachable.isEmpty()) {
                LOGGER.debug("[Telegraph] SendMessagePacket: no reachable machines from {}", pkt.pos);
                player.sendSystemMessage(Component.translatable("message.telegraph.no_targets"));
                return;
            }

            // Only positions that are both selected and actually reachable get a delivery
            Set<BlockPos> allowed = new HashSet<>();
            for (TelegraphTarget target : reachable) {
                if (requested.contains(target.pos())) {
                    allowed.add(target.pos());
                }
            }
            if (allowed.isEmpty()) {
                LOGGER.debug("[Telegraph] SendMessagePacket: no recipient selected at {}", pkt.pos);
                player.sendSystemMessage(Component.translatable("message.telegraph.no_recipient"));
                return;
            }

            LOGGER.debug("[Telegraph] SendMessagePacket: scheduling delivery from {} on ch {} to {} target(s)",
                    pkt.pos, pkt.channel, allowed.size());

            // Schedule delivery to the selected machines only
            int delivered = MessageDeliverySystem.schedule(level, pkt.pos, pkt.message,
                    player.getName().getString(), tbe.getStationName(), pkt.channel,
                    currentTick, allowed);

            if (delivered < 0) {
                player.sendSystemMessage(Component.translatable("message.telegraph.not_enough_paper"));
                return;
            }
            if (delivered == 0) {
                player.sendSystemMessage(Component.translatable("message.telegraph.none_delivered"));
                return;
            }

            // Sender feedback: sound + chat with quality info
            level.playSound(null, pkt.pos, ModSounds.TELEGRAPH_BEEP.get(),
                    SoundSource.BLOCKS, ModSounds.TELEGRAPH_VOLUME, 1.2f);

            // Best quality among the selected targets that are on this channel
            int bestQuality = reachable.stream()
                    .filter(t -> allowed.contains(t.pos()))
                    .filter(t -> t.channel() == pkt.channel)
                    .mapToInt(TelegraphTarget::quality)
                    .max().orElse(0);

            tbe.setLastSignalQuality(bestQuality);

            if (TelegraphConfig.ENABLE_QUALITY_EFFECTS.get()) {
                player.sendSystemMessage(Component.translatable(
                        "message.telegraph.sent_to_quality", delivered, bestQuality));
            } else {
                player.sendSystemMessage(Component.translatable("message.telegraph.sent_to", delivered));
            }

            COOLDOWNS.put(uuid, currentTick);
        });
        ctx.get().setPacketHandled(true);
    }
}
