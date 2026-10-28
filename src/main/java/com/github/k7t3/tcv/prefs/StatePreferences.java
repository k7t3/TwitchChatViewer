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

package com.github.k7t3.tcv.prefs;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;

import com.github.k7t3.tcv.app.group.ChannelGroupOrder;
import java.util.prefs.Preferences;

public class StatePreferences extends PreferencesBase {

    private static final PreferenceKey<Boolean> TOGGLE_CHANNELS = PreferenceKey.bool("toggle.channels", true);
    private static final PreferenceKey<Boolean> ONLY_LIVE = PreferenceKey.bool("only.live", false);
    private static final PreferenceKey<Boolean> ONLY_FOLLOWS = PreferenceKey.bool("only.follows", false);
    private static final PreferenceKey<Boolean> GROUP_ORDER_DESCENDING = PreferenceKey.bool("group.order.descending", false);
    private static final PreferenceKey<String> GROUP_ORDER_ITEM = new PreferenceKey<>("group.order.item",
            () -> ChannelGroupOrder.NAME.name(), value -> ChannelGroupOrder.valueOf(value).name(), value -> value);

    static java.util.Set<String> storedKeys() {
        return java.util.Set.of(TOGGLE_CHANNELS.name(), ONLY_LIVE.name(), ONLY_FOLLOWS.name(),
                GROUP_ORDER_DESCENDING.name(), GROUP_ORDER_ITEM.name());
    }

    private BooleanProperty toggleChannels;
    private BooleanProperty onlyLive;
    private BooleanProperty onlyFollows;
    private BooleanProperty groupOrderDescending;
    private StringProperty groupOrderItem;

    StatePreferences(Preferences preferences) { super(preferences); }
    StatePreferences(PreferenceStore store) { super(store, java.util.Set.of()); }

    public BooleanProperty toggleChannelsProperty() {
        if (toggleChannels == null) toggleChannels = createBooleanProperty(TOGGLE_CHANNELS);
        return toggleChannels;
    }
    public boolean isToggleChannels() { return toggleChannelsProperty().get(); }
    public void setToggleChannels(boolean toggleChannels) { toggleChannelsProperty().set(toggleChannels); }

    public BooleanProperty onlyLiveProperty() {
        if (onlyLive == null) onlyLive = createBooleanProperty(ONLY_LIVE);
        return onlyLive;
    }
    public void setOnlyLive(boolean onlyLive) { onlyLiveProperty().set(onlyLive); }
    public boolean isOnlyLive() { return onlyLiveProperty().get(); }

    public BooleanProperty onlyFollowsProperty() {
        if (onlyFollows == null) onlyFollows = createBooleanProperty(ONLY_FOLLOWS);
        return onlyFollows;
    }
    public boolean isOnlyFollows() { return onlyFollowsProperty().get(); }
    public void setOnlyFollows(boolean onlyFollows) { onlyFollowsProperty().set(onlyFollows); }

    public BooleanProperty groupOrderDescendingProperty() {
        if (groupOrderDescending == null) groupOrderDescending = createBooleanProperty(GROUP_ORDER_DESCENDING);
        return groupOrderDescending;
    }
    public boolean isGroupOrderDescending() { return groupOrderDescendingProperty().get(); }
    public void setGroupOrderDescending(boolean groupOrderDescending) { groupOrderDescendingProperty().set(groupOrderDescending); }

    public StringProperty groupOrderItemProperty() {
        if (groupOrderItem == null) groupOrderItem = createStringProperty(GROUP_ORDER_ITEM);
        return groupOrderItem;
    }
    public String getGroupOrderItem() { return groupOrderItemProperty().get(); }
    public void setGroupOrderItem(String groupOrderItem) { groupOrderItemProperty().set(groupOrderItem); }

}
