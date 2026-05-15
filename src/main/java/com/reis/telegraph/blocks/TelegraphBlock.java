package com.reis.telegraph.blocks;

import com.reis.telegraph.network.PacketHandler;
import com.reis.telegraph.network.packets.OpenGuiPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class TelegraphBlock extends Block implements EntityBlock {

    public TelegraphBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
                                  Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof TelegraphBlockEntity tbe) {
                // Priority: deliver any pending Telegram items first
                if (!tbe.getPendingItems().isEmpty()) {
                    ItemStack item = tbe.getPendingItems().remove(0);
                    tbe.setChanged();
                    if (!player.getInventory().add(item)) {
                        player.drop(item, false);
                    }
                } else {
                    // Open the compose GUI on the client
                    PacketHandler.sendToClient(
                            new OpenGuiPacket(pos, tbe.getChannel(),
                                    tbe.getStationName(), tbe.getLastSignalQuality()),
                            (ServerPlayer) player
                    );
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TelegraphBlockEntity(pos, state);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }
}
