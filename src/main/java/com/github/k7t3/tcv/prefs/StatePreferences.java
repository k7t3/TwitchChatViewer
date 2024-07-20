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

import java.util.Map;
import java.util.prefs.Preferences;

public class StatePreferences extends PreferencesBase {

    private static final String TOGGLE_CHANNELS = "toggle.channels";
    private static final String ONLY_LIVE = "only.live";
    private static final String ONLY_FOLLOWS = "only.follows";
    private static final String GROUP_ORDER_DESCENDING = "group.order.descending";
    private static final String GROUP_ORDER_ITEM = "group.order.item";

    private BooleanProperty toggleChannels;
    private BooleanProperty onlyLive;
    private BooleanProperty onlyFollows;
    private BooleanProperty groupOrderDescending;
    private StringProperty groupOrderItem;

    StatePreferences(Preferences preferences, Map<String, Object> defaults) {
        super(preferences, defaults);

        defaults.put(TOGGLE_CHANNELS, Boolean.TRUE);
        defaults.put(ONLY_LIVE, Boolean.FALSE);
        defaults.put(ONLY_FOLLOWS, Boolean.FALSE);
        defaults.put(GROUP_ORDER_DESCENDING, Boolean.FALSE);
        defaults.put(GROUP_ORDER_ITEM, "NAME"); // ChannelGroupListViewを参照
    }

    @Override
    protected void readFromPreferences() {
        boolean v;
        if ((v = getBoolean(TOGGLE_CHANNELS)) != isToggleChannels())
            setToggleChannels(v);
        if ((v = getBoolean(ONLY_LIVE)) != isOnlyLive())
            setOnlyLive(v);
        if ((v = getBoolean(ONLY_FOLLOWS)) != isOnlyFollows())
            setOnlyFollows(v);
        if ((v = getBoolean(GROUP_ORDER_DESCENDING)) != isGroupOrderDescending())
            setGroupOrderDescending(v);

        String item;
        if (!(item = get(GROUP_ORDER_ITEM)).equals(getGroupOrderItem()))
            setGroupOrderItem(item);
    }

    @Override
    protected void writeToPreferences() {
        // no-op
    }

    public BooleanProperty toggleChannelsProperty() {
        if (toggleChannels == null) toggleChannels = createBooleanProperty(TOGGLE_CHANNELS);
        return toggleChannels;
    }
    public boolean isToggleChannels() { return toggleChannels == null ? (boolean) defaults.get(TOGGLE_CHANNELS) : toggleChannels.get(); }
    public void setToggleChannels(boolean toggleChannels) { toggleChannelsProperty().set(toggleChannels); }

    public BooleanProperty onlyLiveProperty() {
        if (onlyLive == null) onlyLive = createBooleanProperty(ONLY_LIVE);
        return onlyLive;
    }
    public void setOnlyLive(boolean onlyLive) { onlyLiveProperty().set(onlyLive); }
    public boolean isOnlyLive() { return onlyLive == null ? (boolean) defaults.get(ONLY_LIVE) : onlyLive.get(); }

    public BooleanProperty onlyFollowsProperty() {
        if (onlyFollows == null) onlyFollows = createBooleanProperty(ONLY_FOLLOWS);
        return onlyFollows;
    }
    public boolean isOnlyFollows() { return onlyFollows == null ? (boolean) defaults.get(ONLY_FOLLOWS) : onlyFollows.get(); }
    public void setOnlyFollows(boolean onlyFollows) { onlyFollowsProperty().set(onlyFollows); }

    public BooleanProperty groupOrderDescendingProperty() {
        if (groupOrderDescending == null) groupOrderDescending = createBooleanProperty(GROUP_ORDER_DESCENDING);
        return groupOrderDescending;
    }
    public boolean isGroupOrderDescending() { return groupOrderDescending == null ? (boolean) defaults.get(GROUP_ORDER_DESCENDING) : groupOrderDescending.get(); }
    public void setGroupOrderDescending(boolean groupOrderDescending) { groupOrderDescendingProperty().set(groupOrderDescending); }

    public StringProperty groupOrderItemProperty() {
        if (groupOrderItem == null) groupOrderItem = createStringProperty(GROUP_ORDER_ITEM);
        return groupOrderItem;
    }
    public String getGroupOrderItem() { return groupOrderItem == null ? (String) defaults.get(GROUP_ORDER_ITEM) : groupOrderItem.get(); }
    public void setGroupOrderItem(String groupOrderItem) { groupOrderItemProperty().set(groupOrderItem); }

}
