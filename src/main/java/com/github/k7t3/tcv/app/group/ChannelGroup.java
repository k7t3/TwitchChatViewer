package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;
import java.util.UUID;

public class ChannelGroup {

    private UUID id;
    private final StringProperty name = new SimpleStringProperty();
    private final ObservableList<TwitchChannelViewModel> channels = FXCollections.observableArrayList();
    private final ObjectProperty<LocalDateTime> createdAt = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDateTime> updatedAt = new SimpleObjectProperty<>();

    public ChannelGroup() {
    }

    public ChannelGroup(UUID id) {
        this.id = id;
    }

    public ObservableList<TwitchChannelViewModel> getChannels() {
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

    public StringProperty nameProperty() { return name; }
    public String getName() { return name.get(); }
    public void setName(String name) { this.name.set(name); }


}
