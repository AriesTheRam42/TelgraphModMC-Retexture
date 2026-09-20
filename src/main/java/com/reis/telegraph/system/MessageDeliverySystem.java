package com.reis.telegraph.system;

import com.mojang.logging.LogUtils;
import com.reis.telegraph.blocks.TelegraphBlockEntity;
import com.reis.telegraph.config.TelegraphConfig;
import com.reis.telegraph.network.NetworkManager;
import com.reis.telegraph.network.packets.SendMessagePacket;
import com.reis.telegraph.registration.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MessageDeliverySystem {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ticks of delay per cable-hop distance. 2 ticks = 0.1 second per block. */
    private static final int TICKS_PER_BLOCK = 2;

    private static final List<ScheduledDelivery> QUEUE = new ArrayList<>();

    /** Broadcast to every connected machine on the channel (admin command path). */
    public static int schedule(ServerLevel level, BlockPos senderPos,
                                String message, String sender, String senderStation,
                                int channel, long currentTick) {
        return schedule(level, senderPos, message, sender, senderStation, channel, currentTick, null);
    }

    /**
     * Schedule delivery of a message to the machines connected to senderPos
     * on the given channel, with a delay proportional to cable distance.
     * Quality-gated: messages on broken/extremely long lines may be dropped or delayed extra.
     *
     * @param allowedTargets when non-null, only these positions receive the message;
     *                       null keeps the old broadcast-to-everyone behaviour.
     * @return how many deliveries were queued, or -1 if there is not enough paper
     */
    public static int schedule(ServerLevel level, BlockPos senderPos,
                                String message, String sender, String senderStation,
                                int channel, long currentTick, Set<BlockPos> allowedTargets) {
        Map<BlockPos, NetworkManager.NetworkPath> targets =
                NetworkManager.findConnectedMachinesWithPaths(level, senderPos);
        LOGGER.debug("[Telegraph] schedule: BFS found {} machine(s) from {}", targets.size(), senderPos);

        List<ScheduledDelivery> pending = new ArrayList<>();
        for (Map.Entry<BlockPos, NetworkManager.NetworkPath> entry : targets.entrySet()) {
            BlockPos targetPos = entry.getKey();
            if (targetPos.equals(senderPos)) continue; // skip sender machine

            // Honour the sender's recipient selection before anything else
            if (allowedTargets != null && !allowedTargets.contains(targetPos)) {
                LOGGER.debug("[Telegraph] schedule: target {} not selected by sender — SKIPPED", targetPos);
                continue;
            }

            // Filter by channel at scheduling time — the receiver must be on the same channel
            BlockEntity targetBe = level.getBlockEntity(targetPos);
            if (!(targetBe instanceof TelegraphBlockEntity targetTbe)) {
                LOGGER.debug("[Telegraph] schedule: {} has no TelegraphBlockEntity — skipped", targetPos);
                continue;
            }

            int targetChannel = targetTbe.getChannel();
            if (targetChannel != channel) {
                LOGGER.debug("[Telegraph] schedule: target {} is on ch {}, required ch {} — SKIPPED",
                        targetPos, targetChannel, channel);
                continue;
            }

            NetworkManager.NetworkPath path = entry.getValue();
            int quality = SignalQualityCalculator.calculateQuality(path);

            // Drop delivery on broken lines (quality < 5) when effects are enabled
            if (TelegraphConfig.ENABLE_QUALITY_EFFECTS.get() && quality < 5) {
                LOGGER.debug("[Telegraph] schedule: quality {} too low for {} — discarded", quality, targetPos);
                continue;
            }

            int distance = path.distance();
            long baseDelay = (long) distance * TICKS_PER_BLOCK;
            // Low quality (< 30) doubles the propagation delay
            long deliverAt = currentTick + (quality < 30 ? baseDelay * 2 : baseDelay);

            pending.add(new ScheduledDelivery(level.dimension(), targetPos, message, sender,
                    senderStation, channel, deliverAt, quality));
            LOGGER.debug("[Telegraph] schedule: target {} ch {} dist {} quality {} — SCHEDULED at tick {}",
                    targetPos, targetChannel, distance, quality, deliverAt);
        }

        if (pending.isEmpty()) return 0;
        // GUI sends pay per selected recipient; broadcasts pay per valid target.
        int paperCost = allowedTargets == null ? pending.size() : allowedTargets.size();
        if (!TelegraphPaperSupply.consumePaper(level, senderPos, paperCost)) return -1;
        QUEUE.addAll(pending);
        LOGGER.debug("[Telegraph] schedule: {} delivery(s) queued from {} on ch {}", pending.size(), senderPos, channel);
        return pending.size();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
        SendMessagePacket.clearCooldowns();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || QUEUE.isEmpty()) return;

        long currentTick = server.overworld().getGameTime();

        QUEUE.removeIf(delivery -> {
            if (delivery.deliverAtTick > currentTick) return false; // not yet

            ServerLevel level = server.getLevel(delivery.dimension);
            if (level == null || !level.isLoaded(delivery.targetPos)) return true; // discard

            BlockEntity be = level.getBlockEntity(delivery.targetPos);
            if (be instanceof TelegraphBlockEntity tbe) {
                LOGGER.debug("[Telegraph] delivering message to {} on ch {} (quality {})",
                        delivery.targetPos, delivery.channel, delivery.quality);
                tbe.receiveMessage(delivery.message, delivery.sender, delivery.senderStation,
                        delivery.channel, currentTick);
                tbe.setLastSignalQuality(delivery.quality);
                level.playSound(null, delivery.targetPos,
                        ModSounds.TELEGRAPH_BEEP.get(), SoundSource.BLOCKS, ModSounds.TELEGRAPH_VOLUME, 0.8f);
            } else {
                LOGGER.debug("[Telegraph] delivery target {} is gone or not a TelegraphBlockEntity — discarded",
                        delivery.targetPos);
            }
            return true; // remove from queue regardless
        });
    }

    private record ScheduledDelivery(
            ResourceKey<Level> dimension,
            BlockPos targetPos,
            String message,
            String sender,
            String senderStation,
            int channel,
            long deliverAtTick,
            int quality
    ) {}
}
