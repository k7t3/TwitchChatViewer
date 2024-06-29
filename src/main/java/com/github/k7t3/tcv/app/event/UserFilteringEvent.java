package com.github.k7t3.tcv.app.event;

import com.github.k7t3.tcv.app.chat.filter.UserFilterEntry;

public record UserFilteringEvent(UserFilterEntry entry) implements AppEvent {
}
