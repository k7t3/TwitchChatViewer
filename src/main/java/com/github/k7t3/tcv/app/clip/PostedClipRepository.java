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

package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.demo.DEMOBroadcasterProvider;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.event.ClipPostedAppEvent;
import com.github.k7t3.tcv.app.reactive.DownCastFXSubscriber;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomEvent;
import com.github.k7t3.tcv.domain.event.chat.ClipPostedEvent;
import com.github.k7t3.tcv.reactive.FlowableSubscriber;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.NumberBinding;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;

import java.util.List;

public class PostedClipRepository extends AbstractViewModel {

    private final ObservableMap<String, PostedClipItem> items = FXCollections.observableHashMap();

    private ClipThumbnailStore thumbnailStore;

    private FlowableSubscriber<ChatRoomEvent> subscriber;

    public PostedClipRepository() {
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        subscriber = new DownCastFXSubscriber<>(ClipPostedEvent.class, this::onClipPostedEvent);
        eventSubscribers.subscribeChatEvent(subscriber);
    }

    private void onClipPostedEvent(ClipPostedEvent e) {
        var chatRoom = e.getChatRoom();
        var broadcaster = DEMOBroadcasterProvider.provide(chatRoom.getBroadcaster());
        var clipChatMessage = e.getClipChatMessage();

        var id = clipChatMessage.getId();
        var url = clipChatMessage.getEstimatedURL();
        var clip = clipChatMessage.getClip().orElse(null);

        var item = items.get(id);
        if (item == null) {
            item = new PostedClipItem(this, id, url, clip);
            item.onPosted(broadcaster);
            items.put(id, item);
        } else {
            item.onPosted(broadcaster);
        }

        publishEvent();
    }

    private void publishEvent() {
        publish(new ClipPostedAppEvent());
    }

    public ObservableMap<String, PostedClipItem> getItems() {
        return items;
    }

    public NumberBinding getCountBinding() {
        return Bindings.size(items);
    }

    public List<Broadcaster> getPostedBroadcasters() {
        return items.values()
                .stream()
                .flatMap(p -> p.getPostedChannels().stream())
                .distinct()
                .toList();
    }

    ClipThumbnailStore getThumbnailStore() {
        if (thumbnailStore == null) {
            thumbnailStore = new ClipThumbnailStore();
        }
        return thumbnailStore;
    }

    @Override
    public void onLogout() {
        if (subscriber != null) {
            subscriber.cancel();
        }
        items.clear();
    }

    @Override
    public void close() {
        // no-op
    }
}
