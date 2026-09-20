package com.reis.telegraph.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.reis.telegraph.blocks.TelegraphBlock;
import com.reis.telegraph.blocks.TelegraphBlockEntity;
import com.reis.telegraph.system.MessageDeliverySystem;
import com.reis.telegraph.system.TelegraphPaperSupply;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.reis.telegraph.registration.ModSounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Admin/debug command: /telegraph <message> [channel]
 * Kept for server operators. Normal players use the GUI.
 */
public class TelegraphCommand {

    private static final Map<UUID, Long> COOLDOWNS = new HashMap<>();
    private static final long COOLDOWN_TICKS = 600L;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("telegraph")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> sendMessage(ctx.getSource(),
                                StringArgumentType.getString(ctx, "message"), 0))
                        .then(Commands.argument("channel", IntegerArgumentType.integer(0, 99))
                                .executes(ctx -> sendMessage(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "message"),
                                        IntegerArgumentType.getInteger(ctx, "channel"))))));
    }

    private static int sendMessage(CommandSourceStack source, String message, int channel) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel level = player.serverLevel();
            long currentTick = level.getGameTime();

            // Cooldown
            UUID uuid = player.getUUID();
            Long lastSend = COOLDOWNS.get(uuid);
            if (lastSend != null && currentTick - lastSend < COOLDOWN_TICKS) {
                long secsLeft = (COOLDOWN_TICKS - (currentTick - lastSend)) / 20L;
                player.sendSystemMessage(Component.translatable("message.telegraph.cooldown", secsLeft));
                return 0;
            }

            // Find nearest telegraph machine within 3 blocks
            BlockPos playerPos = player.blockPosition();
            BlockPos senderMachine = null;
            for (BlockPos pos : BlockPos.betweenClosed(
                    playerPos.offset(-3, -3, -3), playerPos.offset(3, 3, 3))) {
                if (player.level().getBlockState(pos).getBlock() instanceof TelegraphBlock) {
                    senderMachine = pos.immutable();
                    break;
                }
            }

            if (senderMachine == null) {
                player.sendSystemMessage(Component.literal("[Telegraph] No machine nearby!"));
                return 0;
            }

            if (!TelegraphPaperSupply.hasPaper(level, senderMachine)) {
                player.sendSystemMessage(Component.translatable("message.telegraph.no_paper"));
                return 0;
            }

            BlockEntity machineBe = level.getBlockEntity(senderMachine);
            String stationName = (machineBe instanceof TelegraphBlockEntity tbe)
                    ? tbe.getStationName() : "";

            int delivered = MessageDeliverySystem.schedule(level, senderMachine, message,
                    player.getName().getString(), stationName, channel, currentTick);

            if (delivered < 0) {
                player.sendSystemMessage(Component.translatable("message.telegraph.not_enough_paper"));
                return 0;
            }
            if (delivered == 0) {
                player.sendSystemMessage(Component.translatable("message.telegraph.none_delivered"));
                return 0;
            }

            level.playSound(null, senderMachine, ModSounds.TELEGRAPH_BEEP.get(),
                    SoundSource.BLOCKS, ModSounds.TELEGRAPH_VOLUME, 1.2f);

            player.sendSystemMessage(Component.translatable("message.telegraph.sent"));
            COOLDOWNS.put(uuid, currentTick);
            return 1;

        } catch (Exception e) {
            source.sendFailure(Component.literal("[Telegraph] Must be a player!"));
            return 0;
        }
    }
}
