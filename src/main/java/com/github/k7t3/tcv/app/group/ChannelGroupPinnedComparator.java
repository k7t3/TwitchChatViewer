package com.github.k7t3.tcv.app.group;

import java.util.Comparator;

public interface ChannelGroupPinnedComparator extends Comparator<ChannelGroup> {

    Comparator<ChannelGroup> INSTANCE = new ChannelGroupPinnedComparator() { }.reversed();

    @Override
    default int compare(ChannelGroup o1, ChannelGroup o2) {
        return Boolean.compare(o1.isPinned(), o2.isPinned());
    }
}
