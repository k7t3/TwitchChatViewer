package com.github.k7t3.tcv.app.event;

import com.github.k7t3.tcv.app.service.LiveStateNotificator;

public class LiveNotificationEvent implements AppEvent {

    private final LiveStateNotificator.LiveStateRecord record;

    public LiveNotificationEvent(LiveStateNotificator.LiveStateRecord record) {
        this.record = record;
    }

    public LiveStateNotificator.LiveStateRecord getRecord() {
        return record;
    }
}
