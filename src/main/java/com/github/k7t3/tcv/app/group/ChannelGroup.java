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

package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.UUID;

public class ChannelGroup {

    private UUID id;
    private final StringProperty name = new SimpleStringProperty();
    private StringProperty comment;
    private final ObservableSet<TwitchChannelViewModel> channels = FXCollections.observableSet(new HashSet<>());
    private final BooleanProperty pinned = new SimpleBooleanProperty();
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> updatedAt = new SimpleObjectProperty<>();

    public ChannelGroup() {
    }

    public ChannelGroup(UUID id) {
        this.id = id;
    }

    public ObservableSet<TwitchChannelViewModel> getChannels() {
        return channels;
    }

    public UUID getId() {
        return id;
    }

    public void generateId() {
        if (id != null) throw new IllegalStateException();
        id = UUID.randomUUID();
    }

    // ******************** PROPERTIES ********************

    public ObjectProperty<LocalDateTime> createdAtProperty() { return createdAt; }
    public LocalDateTime getCreatedAt() { return createdAt.get(); }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt.set(createdAt); }

    public ObjectProperty<LocalDateTime> updatedAtProperty() { return updatedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt.get(); }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt.set(updatedAt); }

    public BooleanProperty pinnedProperty() { return pinned; }
    public boolean isPinned() { return pinned.get(); }
    public void setPinned(boolean pinned) { this.pinned.set(pinned); }

    public StringProperty nameProperty() { return name; }
    public String getName() { return name.get(); }
    public void setName(String name) { this.name.set(name); }

    public StringProperty commentProperty() {
        if (comment == null) {
            comment = new SimpleStringProperty("");
        }
        return comment;
    }
    public String getComment() { return comment == null ? "" : comment.get(); }
    public void setComment(String comment) { commentProperty().set(comment); }
}
