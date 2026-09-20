package com.reis.telegraph.system;

import com.reis.telegraph.blocks.TelegraphBlockEntity;
import com.reis.telegraph.network.NetworkManager;
import com.reis.telegraph.network.TelegraphTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Builds the list of telegraph machines reachable from a sender, in a stable
 * order, annotated with the sender's saved routing selection. Shared by the
 * GUI-open path and the send path so both see exactly the same list.
 */
public class TelegraphRouting {

    /** Sorted nearest-first; ties broken by coordinates so the GUI order never jitters. */
    private static final Comparator<TelegraphTarget> ORDER =
            Comparator.comparingInt(TelegraphTarget::distance)
                      .thenComparingInt(t -> t.pos().getX())
                      .thenComparingInt(t -> t.pos().getY())
                      .thenComparingInt(t -> t.pos().getZ());

    public static List<TelegraphTarget> collectTargets(Level level, BlockPos senderPos,
                                                       TelegraphBlockEntity sender) {
        Map<BlockPos, NetworkManager.NetworkPath> reachable =
                NetworkManager.findConnectedMachinesWithPaths(level, senderPos);

        List<TelegraphTarget> targets = new ArrayList<>();
        for (Map.Entry<BlockPos, NetworkManager.NetworkPath> entry : reachable.entrySet()) {
            BlockPos pos = entry.getKey();
            if (pos.equals(senderPos)) continue; // never route to yourself

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof TelegraphBlockEntity tbe)) continue;

            NetworkManager.NetworkPath path = entry.getValue();
            targets.add(new TelegraphTarget(
                    pos.immutable(),
                    tbe.getStationName(),
                    tbe.getChannel(),
                    path.distance(),
                    SignalQualityCalculator.calculateQuality(path),
                    sender == null || sender.isTargetSelected(pos)));
        }

        targets.sort(ORDER);
        if (targets.size() > TelegraphTarget.MAX_TARGETS) {
            targets = new ArrayList<>(targets.subList(0, TelegraphTarget.MAX_TARGETS));
        }
        return targets;
    }
}
