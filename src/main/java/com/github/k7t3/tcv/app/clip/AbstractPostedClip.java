package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.domain.channel.Broadcaster;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractPostedClip implements PostedClip {

    protected final ReadOnlyIntegerWrapper times = new ReadOnlyIntegerWrapper(0);

    protected final Set<Broadcaster> postedChannels = new HashSet<>();

    protected final ReadOnlyObjectWrapper<LocalDateTime> lastPostedAt = new ReadOnlyObjectWrapper<>();

    @Override
    public void onPosted(Broadcaster broadcaster) {
        postedChannels.add(broadcaster);
        lastPostedAt.set(LocalDateTime.now());
        times.set(times.get() + 1);
    }

    @Override
    public boolean isPosted(Broadcaster broadcaster) {
        return postedChannels.contains(broadcaster);
    }

    @Override
    public Set<Broadcaster> getPostedChannels() {
        return postedChannels;
    }

    // ******************** PROPERTIES ********************

    @Override
    public ReadOnlyIntegerProperty timesProperty() { return times.getReadOnlyProperty(); }
    @Override
    public int getTimes() { return times.get(); }

}
