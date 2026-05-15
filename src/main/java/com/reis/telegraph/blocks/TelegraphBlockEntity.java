package com.reis.telegraph.blocks;

import com.reis.telegraph.items.TelegraphMessageItem;
import com.reis.telegraph.registration.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class TelegraphBlockEntity extends BlockEntity {

    private int channel = 0;
    private final List<ItemStack> pendingItems = new ArrayList<>();
    private static final int MAX_PENDING = 20;
    private String stationName = "";
    private int lastSignalQuality = -1; // -1 = never measured

    public TelegraphBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TELEGRAPH_ENTITY.get(), pos, state);
    }

    public int getChannel() {
        return channel;
    }

    public void setChannel(int channel) {
        this.channel = Math.max(0, Math.min(99, channel));
        setChanged();
    }

    public List<ItemStack> getPendingItems() {
        return pendingItems;
    }

    public String getStationName() {
        return stationName == null ? "" : stationName;
    }

    public void setStationName(String name) {
        if (name == null) name = "";
        this.stationName = name.replaceAll("[^A-Za-z0-9 _\\-']", "")
                               .substring(0, Math.min(name.length(), 32));
        setChanged();
    }

    public int getLastSignalQuality() {
        return lastSignalQuality;
    }

    public void setLastSignalQuality(int quality) {
        this.lastSignalQuality = Math.max(-1, Math.min(100, quality));
        setChanged();
    }

    /**
     * Called by the delivery system when a message arrives at this machine.
     * Always stores the Telegram item inside the machine — player must right-click to collect.
     */
    public void receiveMessage(String message, String sender, String senderStation,
                               int ch, long timestamp) {
        if (level == null || level.isClientSide) return;

        if (pendingItems.size() < MAX_PENDING) {
            pendingItems.add(TelegraphMessageItem.create(message, sender, senderStation, ch, timestamp));
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("Channel", channel);
        tag.putString("StationName", stationName);
        tag.putInt("LastSignalQuality", lastSignalQuality);

        ListTag pendingTag = new ListTag();
        for (ItemStack stack : pendingItems) {
            CompoundTag itemTag = new CompoundTag();
            stack.save(itemTag);
            pendingTag.add(itemTag);
        }
        tag.put("PendingItems", pendingTag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        channel = tag.getInt("Channel");
        stationName = tag.getString("StationName"); // returns "" if absent — safe default
        lastSignalQuality = tag.contains("LastSignalQuality") ? tag.getInt("LastSignalQuality") : -1;

        pendingItems.clear();
        ListTag pendingTag = tag.getList("PendingItems", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingTag.size(); i++) {
            ItemStack stack = ItemStack.of(pendingTag.getCompound(i));
            if (!stack.isEmpty()) {
                pendingItems.add(stack);
            }
        }
    }
}
