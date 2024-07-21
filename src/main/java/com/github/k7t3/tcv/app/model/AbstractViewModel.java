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

package com.github.k7t3.tcv.app.model;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.event.LogoutEvent;
import com.github.k7t3.tcv.app.event.ShutdownEvent;
import com.github.k7t3.tcv.domain.event.EventSubscribers;

/**
 * ログイン時、ログアウト時、アプリケーションの終了時に処理が自動でトリガーするベースクラス
 * <p>
 *     ログイン時に{@link #subscribeEvents(EventSubscribers)}メソッドが、
 *     ログアウト時は{@link #onLogout()}、終了時には{@link #close()}がコールされる。
 * </p>
 */
public abstract class AbstractViewModel implements AppViewModel {

    protected final TypeSafeNotificationObserver<LoginEvent> onLogin = this::onLogin;
    protected final TypeSafeNotificationObserver<LogoutEvent> onLogout = this::onLogout;
    protected final TypeSafeNotificationObserver<ShutdownEvent> onShutdown = this::onShutdown;

    public AbstractViewModel() {
        subscribe(LoginEvent.class, onLogin);
        subscribe(LogoutEvent.class, onLogout);
        subscribe(ShutdownEvent.class, onShutdown);
    }

    private void onLogin(LoginEvent loginEvent) {
        var helper = AppHelper.getInstance();
        var subscribers = helper.getSubscribers();
        subscribeEvents(subscribers);
    }

    private void onLogout(LogoutEvent logoutEvent) {
        onLogout();
    }

    private void onShutdown(ShutdownEvent shutdownEvent) {
        close();
    }

}
