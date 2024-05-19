package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.channel.TwitchChannelListener;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalTime;
import java.util.LinkedList;

public class LiveStateNotificator implements TwitchChannelListener {

    private static final int RECORD_COUNT = 40;

    private final ObservableList<LiveStateRecord> records = FXCollections.observableList(new LinkedList<>());

    private void push(LiveStateRecord record) {
        int over = (records.size() + 1) - RECORD_COUNT;
        if (0 < over) {
            for (int i = 0; i < over; i++)
                records.removeFirst();
        }
        records.add(record);
    }

    public ObservableList<LiveStateRecord> getRecords() {
        return records;
    }

    @Override
    public void onOnline(TwitchChannel channel, StreamInfo info) {
        Platform.runLater(() -> {
            var name = channel.getBroadcaster().getUserName();
            var time = LocalTime.now();
            push(new LiveStateRecord(name, time, true));
        });
    }

    @Override
    public void onOffline(TwitchChannel channel) {
        Platform.runLater(() -> {
            var name = channel.getChannelName();
            var time = LocalTime.now();
            push(new LiveStateRecord(name, time, false));
        });
    }

    @Override
    public void onViewerCountUpdated(TwitchChannel channel, StreamInfo info) {
        // no-op
    }

    @Override
    public void onTitleChanged(TwitchChannel channel, StreamInfo info) {
        // no-op
    }

    @Override
    public void onGameChanged(TwitchChannel channel, StreamInfo info) {
        // no-op
    }

    public record LiveStateRecord(String channelName, LocalTime time, boolean live) {}

}
