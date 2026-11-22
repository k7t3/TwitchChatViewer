package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.support.MemoryPreferences;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class StatePreferencesReadTest {
    @ParameterizedTest
    @ValueSource(strings = {"toggle.channels", "only.live", "only.follows", "group.order.descending"})
    void aColdGetterReadsTheStoredValueBeforeAnyPropertyHasBeenRequested(String key) {
        var node = new MemoryPreferences();
        var stored = !key.equals("toggle.channels");
        node.putBoolean(key, stored);
        var state = new AppPreferences(node).getStatePreferences();
        var actual = switch (key) {
            case "toggle.channels" -> state.isToggleChannels();
            case "only.live" -> state.isOnlyLive();
            case "only.follows" -> state.isOnlyFollows();
            case "group.order.descending" -> state.isGroupOrderDescending();
            default -> throw new AssertionError();
        };
        assertThat(actual).isEqualTo(stored);
        assertThat(node.getBoolean(key, !stored)).isEqualTo(stored);
    }

    @org.junit.jupiter.api.Test
    void aColdGroupOrderGetterReadsItsStoredSelection() {
        var node = new MemoryPreferences();
        node.put("group.order.item", "UPDATED_AT");
        var state = new AppPreferences(node).getStatePreferences();
        assertThat(state.getGroupOrderItem()).isEqualTo("UPDATED_AT");
    }
}
