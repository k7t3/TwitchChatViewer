/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.event.LiveNotificationEvent;
import com.github.k7t3.tcv.app.reactive.DownCastFXSubscriber;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.channel.ChannelOfflineEvent;
import com.github.k7t3.tcv.domain.event.channel.ChannelOnlineEvent;
import com.github.k7t3.tcv.domain.event.channel.TwitchChannelEvent;
import com.github.k7t3.tcv.reactive.FlowableSubscriber;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LiveStateNotificator extends AbstractViewModel {

    private static final int RECORD_COUNT = 40;

    public record LiveStateRecord(TwitchChannelViewModel channel, LocalTime time, boolean live) {}

    private final ObservableList<LiveStateRecord> records = FXCollections.observableList(new LinkedList<>());
    private final ChannelViewModelRepository repository;
    private final List<FlowableSubscriber<?>> subscribers = new ArrayList<>();

    public LiveStateNotificator(ChannelViewModelRepository repository) {
        this.repository = repository;
    }

    private void push(LiveStateRecord record) {
        int over = (records.size() + 1) - RECORD_COUNT;
        if (0 < over) {
            for (int i = 0; i < over; i++)
                records.removeFirst();
        }
        records.add(record);

        // イベントを発行
        publish(new LiveNotificationEvent(record));
    }

    public ObservableList<LiveStateRecord> getRecords() {
        return records;
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        var onlineSub = new DownCastFXSubscriber<TwitchChannelEvent, ChannelOnlineEvent>(ChannelOnlineEvent.class, this::onOnlineEvent);
        var offlineSub = new DownCastFXSubscriber<TwitchChannelEvent, ChannelOfflineEvent>(ChannelOfflineEvent.class, this::onOfflineEvent);
        eventSubscribers.subscribeChannelEvent(onlineSub);
        eventSubscribers.subscribeChannelEvent(offlineSub);
        subscribers.addAll(List.of(onlineSub, offlineSub));
    }

    private void onOnlineEvent(ChannelOnlineEvent e) {
        var broadcaster = e.getChannel().getBroadcaster();
        repository.getChannelAsync(broadcaster).onDone(channel -> {
            var time = LocalTime.now();
            var record = new LiveStateRecord(channel, time, true);
            push(record);
        });
    }

    private void onOfflineEvent(ChannelOfflineEvent e) {
        var broadcaster = e.getChannel().getBroadcaster();
        repository.getChannelAsync(broadcaster).onDone(channel -> {
            var time = LocalTime.now();
            var record = new LiveStateRecord(channel, time, false);
            push(record);
        });
    }

    @Override
    public void onLogout() {
        for (var sub : subscribers)
            sub.cancel();
        subscribers.clear();
        records.clear();
    }

    @Override
    public void close() {
        for (var sub : subscribers)
            sub.cancel();
        subscribers.clear();
    }

}
