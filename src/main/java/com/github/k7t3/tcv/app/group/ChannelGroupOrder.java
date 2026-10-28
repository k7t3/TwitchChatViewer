package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import java.util.Comparator;

public enum ChannelGroupOrder implements Comparator<ChannelGroup> {

    UPDATED_AT("group.comparator.updated", Comparator.comparing(ChannelGroup::getUpdatedAt)),

    CREATED_AT("group.comparator.created", Comparator.comparing(ChannelGroup::getCreatedAt)),

    LIVE_COUNT("group.comparator.liver", Comparator.comparingLong(g -> g.getChannels()
            .stream()
            .filter(TwitchChannelViewModel::isLive)
            .count()
    )),

    /** 名前は既存の保存形式として維持する。 */
    NAME("group.comparator.name", Comparator.comparing(ChannelGroup::getName));

    private final String display;

    private final Comparator<ChannelGroup> comparator;

    ChannelGroupOrder(String display, Comparator<ChannelGroup> comparator) {
        this.display = display;
        this.comparator = comparator;
    }

    @Override
    public String toString() {
        return Resources.getString(display);
    }

    @Override
    public int compare(ChannelGroup o1, ChannelGroup o2) {
        return comparator.compare(o1, o2);
    }
}
