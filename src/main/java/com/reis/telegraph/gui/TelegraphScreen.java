package com.reis.telegraph.gui;

import com.reis.telegraph.config.TelegraphConfig;
import com.reis.telegraph.network.PacketHandler;
import com.reis.telegraph.network.TelegraphTarget;
import com.reis.telegraph.network.packets.SendMessagePacket;
import com.reis.telegraph.network.packets.SetChannelPacket;
import com.reis.telegraph.network.packets.SetStationNamePacket;
import com.reis.telegraph.network.packets.SetTargetsPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
public class TelegraphScreen extends Screen {

    private final BlockPos machinePos;
    private final int currentChannel;
    private final String initialStationName;
    private final int lastSignalQuality;

    /** Stations reachable from this machine, nearest first, as computed by the server. */
    private final List<TelegraphTarget> targets;
    /** Positions currently ticked in the recipient list. */
    private final Set<BlockPos> selected = new LinkedHashSet<>();

    private EditBox messageBox;
    private EditBox channelBox;
    private EditBox stationNameBox;

    private int scrollOffset = 0;
    private boolean showNoRecipientWarning = false;

    private static final int BG_WIDTH  = 256;
    private static final int BG_HEIGHT = 212;

    private static final int LIST_X_INSET = 10;
    private static final int LIST_TOP     = 96;   // relative to panel top
    private static final int ROW_HEIGHT   = 13;
    private static final int VISIBLE_ROWS = 5;
    private static final int LIST_HEIGHT  = ROW_HEIGHT * VISIBLE_ROWS;

    public TelegraphScreen(BlockPos machinePos, int currentChannel,
                           String stationName, int lastSignalQuality,
                           List<TelegraphTarget> targets) {
        super(Component.translatable("gui.telegraph.title"));
        this.machinePos = machinePos;
        this.currentChannel = currentChannel;
        this.initialStationName = stationName == null ? "" : stationName;
        this.lastSignalQuality = lastSignalQuality;
        this.targets = targets == null ? List.of() : List.copyOf(targets);

        for (TelegraphTarget target : this.targets) {
            if (target.selected()) {
                selected.add(target.pos());
            }
        }
    }

    @Override
    protected void init() {
        int left = (width - BG_WIDTH) / 2;
        int top  = (height - BG_HEIGHT) / 2;

        // Message input
        messageBox = new EditBox(font, left + 10, top + 22, BG_WIDTH - 20, 20,
                Component.translatable("gui.telegraph.message_placeholder"));
        messageBox.setMaxLength(256);
        messageBox.setHint(Component.translatable("gui.telegraph.message_placeholder"));
        addWidget(messageBox);
        setInitialFocus(messageBox);

        // Channel input — digits only, max 2 chars
        channelBox = new EditBox(font, left + 10, top + 58, 34, 18,
                Component.literal(String.valueOf(currentChannel)));
        channelBox.setMaxLength(2);
        channelBox.setValue(String.valueOf(currentChannel));
        channelBox.setFilter(s -> s.matches("\\d*")); // digits only
        addWidget(channelBox);

        // Station name input, on the same row as the channel
        stationNameBox = new EditBox(font, left + 52, top + 58, BG_WIDTH - 62, 18,
                Component.translatable("gui.telegraph.station_name_placeholder"));
        stationNameBox.setMaxLength(32);
        stationNameBox.setValue(initialStationName);
        stationNameBox.setHint(Component.translatable("gui.telegraph.station_name_placeholder"));
        addWidget(stationNameBox);

        // Select-all / select-none shortcuts for the recipient list
        addRenderableWidget(Button.builder(
                Component.translatable("gui.telegraph.select_all"),
                btn -> {
                    selected.clear();
                    for (TelegraphTarget target : targets) selected.add(target.pos());
                    showNoRecipientWarning = false;
                }
        ).pos(left + BG_WIDTH - 78, top + 80).size(34, 14).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.telegraph.select_none"),
                btn -> selected.clear()
        ).pos(left + BG_WIDTH - 42, top + 80).size(34, 14).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.telegraph.send"),
                btn -> onSendClicked()
        ).pos(left + (BG_WIDTH - 100) / 2, top + 172).size(100, 20).build());
    }

    private int readChannel() {
        try {
            return Math.max(0, Math.min(99, Integer.parseInt(channelBox.getValue().trim())));
        } catch (NumberFormatException e) {
            return currentChannel;
        }
    }

    private void onSendClicked() {
        String message = messageBox.getValue().trim();
        if (message.isEmpty()) return;

        if (selected.isEmpty()) {
            showNoRecipientWarning = true;
            return;
        }

        // Commit the station name first — the outgoing telegram is stamped with it
        PacketHandler.sendToServer(new SetStationNamePacket(machinePos,
                stationNameBox.getValue().trim()));
        PacketHandler.sendToServer(new SendMessagePacket(
                machinePos, message, readChannel(), new ArrayList<>(selected)));
        onClose();
    }

    /** Station label for a target — falls back to a generic name when unnamed. */
    private Component targetLabel(TelegraphTarget target) {
        String name = target.stationName();
        if (name == null || name.isBlank()) {
            return Component.translatable("gui.telegraph.unnamed_station",
                    target.pos().getX(), target.pos().getY(), target.pos().getZ());
        }
        return Component.literal(name);
    }

    private int maxScroll() {
        return Math.max(0, targets.size() - VISIBLE_ROWS);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        int left = (width - BG_WIDTH) / 2;
        int top  = (height - BG_HEIGHT) / 2;

        // Background panel
        graphics.fill(left, top, left + BG_WIDTH, top + BG_HEIGHT, 0xCC000000);
        graphics.renderOutline(left, top, BG_WIDTH, BG_HEIGHT, 0xFF888888);

        // Title
        graphics.drawCenteredString(font, title, width / 2, top + 8, 0xFFFFFF);

        // Channel + station labels
        graphics.drawString(font,
                Component.translatable("gui.telegraph.channel_label"),
                left + 10, top + 48, 0xAAAAAA);
        graphics.drawString(font,
                Component.translatable("gui.telegraph.station_name_label"),
                left + 52, top + 48, 0xAAAAAA);

        // Recipients header
        graphics.drawString(font,
                Component.translatable("gui.telegraph.recipients_label", selected.size(), targets.size()),
                left + 10, top + 82, 0xAAAAAA);

        renderTargetList(graphics, left, top, mouseX, mouseY);

        if (showNoRecipientWarning) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.telegraph.no_recipient_warning"),
                    width / 2, top + 163, 0xFFFF5555);
        }

        // Signal quality bar — shown only after first send (quality >= 0) and when effects are on
        if (lastSignalQuality >= 0 && TelegraphConfig.ENABLE_QUALITY_EFFECTS.get()) {
            int barX = left + 10;
            int barY = top + 198;
            int barW = Math.max(1, lastSignalQuality); // 1–100 px wide
            int barColor = lastSignalQuality >= 60 ? 0xFF55FF55
                         : lastSignalQuality >= 30 ? 0xFFFFAA00 : 0xFFFF5555;
            graphics.fill(barX, barY, barX + barW, barY + 5, barColor);
            graphics.renderOutline(barX, barY, 100, 5, 0xFF555555);
            graphics.drawString(font,
                    Component.translatable("gui.telegraph.quality_label", lastSignalQuality),
                    left + 115, barY, 0xAAAAAA);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        messageBox.render(graphics, mouseX, mouseY, partialTick);
        channelBox.render(graphics, mouseX, mouseY, partialTick);
        stationNameBox.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderTargetList(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        int listX = left + LIST_X_INSET;
        int listY = top + LIST_TOP;
        int listW = BG_WIDTH - LIST_X_INSET * 2;

        graphics.fill(listX, listY, listX + listW, listY + LIST_HEIGHT, 0x66000000);
        graphics.renderOutline(listX, listY, listW, LIST_HEIGHT, 0xFF555555);

        if (targets.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.telegraph.no_connections"),
                    listX + listW / 2, listY + LIST_HEIGHT / 2 - 4, 0xFF888888);
            return;
        }

        int channel = readChannel();
        int rows = Math.min(VISIBLE_ROWS, targets.size() - scrollOffset);

        for (int i = 0; i < rows; i++) {
            TelegraphTarget target = targets.get(scrollOffset + i);
            int rowY = listY + i * ROW_HEIGHT;
            boolean isSelected = selected.contains(target.pos());
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                           && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;

            if (hovered) {
                graphics.fill(listX + 1, rowY, listX + listW - 1, rowY + ROW_HEIGHT, 0x33FFFFFF);
            }

            // Checkbox
            int boxX = listX + 3;
            int boxY = rowY + 2;
            graphics.renderOutline(boxX, boxY, 9, 9, isSelected ? 0xFF55FF55 : 0xFF777777);
            if (isSelected) {
                graphics.fill(boxX + 2, boxY + 2, boxX + 7, boxY + 7, 0xFF55FF55);
            }

            // Right-hand info: channel, cable length, signal quality
            Component info = Component.translatable("gui.telegraph.target_info",
                    target.channel(), target.distance(), target.quality());
            int infoWidth = font.width(info);
            // Off-channel stations will not receive the message — flag them
            boolean channelMatch = target.channel() == channel;
            graphics.drawString(font, info, listX + listW - infoWidth - 4, rowY + 3,
                    channelMatch ? 0xFF888888 : 0xFFFF5555);

            // Station name, trimmed to whatever space the info column leaves
            int nameX = boxX + 13;
            int nameSpace = (listX + listW - infoWidth - 8) - nameX;
            String name = targetLabel(target).getString();
            if (font.width(name) > nameSpace) {
                name = font.plainSubstrByWidth(name, Math.max(0, nameSpace - 6)) + "...";
            }
            int nameColor = !isSelected ? 0xFF777777 : channelMatch ? 0xFFFFFFFF : 0xFFFFAA55;
            graphics.drawString(font, name, nameX, rowY + 3, nameColor);
        }

        // Scrollbar — only when there is something to scroll
        if (maxScroll() > 0) {
            int trackX = listX + listW - 3;
            int thumbH = Math.max(8, LIST_HEIGHT * VISIBLE_ROWS / targets.size());
            int thumbY = listY + (LIST_HEIGHT - thumbH) * scrollOffset / maxScroll();
            graphics.fill(trackX, listY, trackX + 2, listY + LIST_HEIGHT, 0xFF333333);
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !targets.isEmpty()) {
            int left = (width - BG_WIDTH) / 2;
            int top  = (height - BG_HEIGHT) / 2;
            int listX = left + LIST_X_INSET;
            int listY = top + LIST_TOP;
            int listW = BG_WIDTH - LIST_X_INSET * 2;

            if (mouseX >= listX && mouseX < listX + listW
             && mouseY >= listY && mouseY < listY + LIST_HEIGHT) {
                int row = (int) ((mouseY - listY) / ROW_HEIGHT) + scrollOffset;
                if (row >= 0 && row < targets.size()) {
                    BlockPos pos = targets.get(row).pos();
                    if (!selected.remove(pos)) {
                        selected.add(pos);
                    }
                    showNoRecipientWarning = false;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll() > 0) {
            int left = (width - BG_WIDTH) / 2;
            int top  = (height - BG_HEIGHT) / 2;
            int listX = left + LIST_X_INSET;
            int listY = top + LIST_TOP;
            int listW = BG_WIDTH - LIST_X_INSET * 2;

            if (mouseX >= listX && mouseX < listX + listW
             && mouseY >= listY && mouseY < listY + LIST_HEIGHT) {
                scrollOffset = Math.max(0, Math.min(maxScroll(), scrollOffset - (int) Math.signum(delta)));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        PacketHandler.sendToServer(new SetChannelPacket(machinePos, readChannel()));
        PacketHandler.sendToServer(new SetStationNamePacket(machinePos,
                stationNameBox.getValue().trim()));
        // Keep the routing even if the player closed without sending
        PacketHandler.sendToServer(new SetTargetsPacket(machinePos, new ArrayList<>(selected)));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
