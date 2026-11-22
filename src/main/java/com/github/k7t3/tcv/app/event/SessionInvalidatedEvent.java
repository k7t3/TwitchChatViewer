package com.github.k7t3.tcv.app.event;

import com.github.k7t3.tcv.app.core.ClientSessions;

/** 同じアプリが所有する失効済みセッションの画面を、FX スレッドで切り離す。 */
public record SessionInvalidatedEvent(ClientSessions source) implements AppEvent {}
