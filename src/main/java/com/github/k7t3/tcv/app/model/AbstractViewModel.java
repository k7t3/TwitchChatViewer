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
