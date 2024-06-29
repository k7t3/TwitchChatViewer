package com.github.k7t3.tcv.app.event;

import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;

public record KeywordFilteringEvent(KeywordFilterEntry entry) implements AppEvent {

}
