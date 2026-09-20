package com.reis.telegraph.system;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Collects paper across adjacent storage, charging the full recipient count at once. */
public final class TelegraphPaperSupply {
    private TelegraphPaperSupply() {}

    public static boolean hasPaper(Level level, BlockPos machinePos) {
        return plan(level, machinePos, 1) != null;
    }

    public static boolean consumePaper(Level level, BlockPos machinePos, int amount) {
        if (level.isClientSide || amount < 1) return false;
        List<Withdrawal> plan = plan(level, machinePos, amount);
        if (plan == null) return false;
        List<Withdrawal> taken = new ArrayList<>();
        for (Withdrawal withdrawal : plan) {
            ItemStack extracted = withdrawal.inventory.extractItem(withdrawal.slot, withdrawal.amount, false);
            if (!extracted.isEmpty()) {
                taken.add(new Withdrawal(withdrawal.inventory, withdrawal.slot, extracted.getCount(), extracted));
            }
            if (!extracted.is(Items.PAPER) || extracted.getCount() != withdrawal.amount) {
                // Restore earlier withdrawals if a modded inventory changes after simulation.
                for (Withdrawal refund : taken) {
                    ItemStack remainder = refund.inventory.insertItem(refund.slot, refund.stack, false);
                    remainder = ItemHandlerHelper.insertItemStacked(refund.inventory, remainder, false);
                    if (!remainder.isEmpty()) {
                        Containers.dropItemStack(level, machinePos.getX() + 0.5,
                                machinePos.getY() + 0.5, machinePos.getZ() + 0.5, remainder);
                    }
                }
                return false;
            }
        }
        return true;
    }

    private static List<Withdrawal> plan(Level level, BlockPos machinePos, int amount) {
        List<Withdrawal> plan = new ArrayList<>();
        Set<IItemHandler> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        int remaining = amount;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos storagePos = machinePos.relative(direction);
            if (!level.hasChunkAt(storagePos)) continue;
            BlockEntity storage = level.getBlockEntity(storagePos);
            if (storage == null) continue;
            // One inventory view prevents counting both sided and unsided views twice.
            IItemHandler inventory = storage.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
            if (inventory == null) {
                inventory = storage.getCapability(ForgeCapabilities.ITEM_HANDLER,
                        direction.getOpposite()).orElse(null);
            }
            if (inventory == null && storage instanceof Container container) {
                inventory = new InvWrapper(container);
            }
            if (inventory == null || !visited.add(inventory)) continue;
            for (int slot = 0; slot < inventory.getSlots(); slot++) {
                if (!inventory.getStackInSlot(slot).is(Items.PAPER)) continue;
                ItemStack available = inventory.extractItem(slot, remaining, true);
                if (!available.is(Items.PAPER)) continue;
                int count = Math.min(remaining, available.getCount());
                plan.add(new Withdrawal(inventory, slot, count, ItemStack.EMPTY));
                remaining -= count;
                if (remaining == 0) return plan;
            }
        }
        return null;
    }

    private record Withdrawal(IItemHandler inventory, int slot, int amount, ItemStack stack) {}
}
