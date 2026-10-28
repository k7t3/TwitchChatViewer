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

import com.github.k7t3.tcv.app.event.AppEvent;
import com.github.k7t3.tcv.app.event.LoginEvent;
import com.github.k7t3.tcv.app.event.LogoutEvent;
import com.github.k7t3.tcv.app.event.ShutdownEvent;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.reactive.FlowableSubscriber;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * ログイン時、ログアウト時、アプリケーションの終了時に処理が自動でトリガーするベースクラス
 * <p>
 *     ログイン時に{@link #subscribeEvents(EventSubscribers)}メソッドが、
 *     ログアウト時は{@link #onLogout()}、終了時には{@link #close()}がコールされる。
 * </p>
 */
public abstract class AbstractViewModel implements AppViewModel {

    private final AtomicBoolean closed = new AtomicBoolean();
    private final List<Registration> registrations = new ArrayList<>();
    private SessionSubscriptions session;
    private EventSubscribers eventSubscribers;

    private record Registration(Class<? extends AppEvent> type,
                                TypeSafeNotificationObserver<?> observer,
                                TypeSafeNotificationObserver<?> forwarding,
                                AtomicBoolean active,
                                SessionSubscriptions owner) {
    }

    protected final TypeSafeNotificationObserver<LoginEvent> onLogin = this::onLogin;
    protected final TypeSafeNotificationObserver<LogoutEvent> onLogout = this::onLogout;
    protected final TypeSafeNotificationObserver<ShutdownEvent> onShutdown = this::onShutdown;

    public AbstractViewModel() {
        try {
            subscribe(LoginEvent.class, onLogin);
            subscribe(LogoutEvent.class, onLogout);
            subscribe(ShutdownEvent.class, onShutdown);
        } catch (RuntimeException | Error failure) {
            closed.set(true);
            try {
                // 子クラスは未構築なので、共通クラスが登録した通知だけを解放する。
                removeRegistrations(registration -> true);
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private void onLogin(LoginEvent loginEvent) {
        if (eventSubscribers != null) subscribeEvents(eventSubscribers);
    }

    /**
     * ログイン通知で Flow を開始するモデルへ、所有者の発行元を渡す。
     * 未設定では Flow を開始しない。購読中の差し替えはログアウト後に行う。
     */
    public final void setEventSubscribers(EventSubscribers source) {
        if (closed.get() || session != null) throw new IllegalStateException("cannot replace active event subscriptions");
        eventSubscribers = Objects.requireNonNull(source);
    }

    /** 親が開始・ログアウト・終了を一度ずつ呼び出すモデルでは、既定の全体通知への接続を外す。 */
    public final void delegateLifecycleToOwner() {
        if (closed.get() || session != null) throw new IllegalStateException("cannot delegate an active lifecycle");
        CloseActions.runAll(() -> unsubscribe(onLogin), () -> unsubscribe(onLogout), () -> unsubscribe(onShutdown));
    }

    @Override
    public final void subscribeEvents(EventSubscribers eventSubscribers) {
        final SessionSubscriptions opening;
        synchronized (registrations) {
            if (closed.get() || session != null) return;
            opening = new SessionSubscriptions();
            session = opening;
        }
        try {
            onSubscribeEvents(eventSubscribers, opening);
        } catch (RuntimeException | Error failure) {
            try {
                opening.close();
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /**
     * Flow とセッション中の通知を、渡された購読範囲へ登録する。
     * 再入 logout / close 後も、この呼び出しには終了済みの同じ範囲が渡され続ける。
     */
    protected void onSubscribeEvents(EventSubscribers eventSubscribers, SessionSubscriptions subscriptions) {
    }

    private void onLogout(LogoutEvent logoutEvent) {
        onLogout();
    }

    @Override
    public final void onLogout() {
        final SessionSubscriptions previous;
        synchronized (registrations) {
            if (closed.get()) return;
            previous = session;
            session = null;
        }
        CloseActions.runAll(() -> { if (previous != null) previous.close(); }, this::onLoggedOut);
    }

    /** セッションの購読を解除した後に実行するログアウト処理。 */
    protected void onLoggedOut() {
    }

    private void onShutdown(ShutdownEvent shutdownEvent) {
        close();
    }

    @Override
    public final <T extends AppEvent> void subscribe(Class<T> eventType, TypeSafeNotificationObserver<T> observer) {
        register(eventType, observer, null);
    }

    private <T extends AppEvent> void register(Class<T> eventType, TypeSafeNotificationObserver<T> observer,
                                              SessionSubscriptions owner) {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(observer);
        final Registration registration;
        final TypeSafeNotificationObserver<T> forwarding;
        synchronized (registrations) {
            if (owner != null && (closed.get() || owner.retired)) return;
            if (closed.get()) throw new IllegalStateException("view model is closed");
            var active = new AtomicBoolean(true);
            forwarding = event -> {
                if (!closed.get() && active.get()) observer.receivedNotification(event);
            };
            registration = new Registration(eventType, observer, forwarding, active, owner);
            registrations.add(registration);
        }
        try {
            AppViewModel.super.subscribe(eventType, forwarding);
            // 登録先からの再入終了より後に戻った登録も、その場で解除する。
            if (!registration.active().get()) AppViewModel.super.unsubscribe(forwarding);
        } catch (RuntimeException | Error failure) {
            synchronized (registrations) {
                registration.active().set(false);
                registrations.remove(registration);
            }
            try {
                AppViewModel.super.unsubscribe(forwarding);
            } catch (RuntimeException | Error cleanup) {
                if (cleanup != failure) failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Override
    public final <T extends AppEvent> void unsubscribe(TypeSafeNotificationObserver<T> observer) {
        removeRegistrations(registration -> registration.observer() == observer);
    }

    @Override
    public final <T extends AppEvent> void unsubscribe(Class<T> eventType, TypeSafeNotificationObserver<T> observer) {
        removeRegistrations(registration -> registration.type() == eventType && registration.observer() == observer);
    }

    private void removeRegistrations(Predicate<Registration> predicate) {
        final List<Registration> removed;
        synchronized (registrations) {
            removed = registrations.stream().filter(predicate).toList();
            registrations.removeAll(removed);
            // 通知中のスナップショットに残る登録も、外部の解除処理を始める前に無効にする。
            removed.forEach(registration -> registration.active().set(false));
        }
        CloseActions.runAll(removed.stream().map(registration -> (Runnable) () ->
                AppViewModel.super.unsubscribe(registration.forwarding())).toArray(Runnable[]::new));
    }

    /** 一回のログインが所有する Flow と通知。終了済みの範囲への遅い登録は採用しない。 */
    protected final class SessionSubscriptions implements AutoCloseable {
        private final List<FlowableSubscriber<?>> subscribers = new ArrayList<>();
        private boolean retired;

        private SessionSubscriptions() {
        }

        /**
         * Flow の開始前に所有を登録する。終了後に渡された購読は直ちに取消す。
         * @param subscriber このセッションだけが所有する購読
         * @return 渡された購読。終了済みの場合は取消済み
         * @param <S> 購読の型
         */
        public <S extends FlowableSubscriber<?>> S own(S subscriber) {
            Objects.requireNonNull(subscriber);
            synchronized (registrations) {
                if (!closed.get() && !retired) {
                    subscribers.add(subscriber);
                    return subscriber;
                }
            }
            subscriber.cancel();
            return subscriber;
        }

        /**
         * logout / close / 登録失敗で解除する通知を登録する。終了済みなら登録しない。
         * @param eventType 購読する通知の型
         * @param observer このセッションの通知処理
         * @param <T> 通知の型
         */
        public <T extends AppEvent> void subscribe(Class<T> eventType, TypeSafeNotificationObserver<T> observer) {
            register(eventType, observer, this);
        }

        /** 通知を無効化してから Flow を取消す。失敗しても全件を試行し、後続の失敗を保持する。 */
        @Override
        public void close() {
            final List<FlowableSubscriber<?>> snapshot;
            synchronized (registrations) {
                if (retired) return;
                retired = true;
                if (session == this) session = null;
                snapshot = List.copyOf(subscribers);
                subscribers.clear();
            }
            CloseActions.runAll(() -> removeRegistrations(registration -> registration.owner() == this),
                    () -> CloseActions.runAll(snapshot.stream().map(subscriber -> (Runnable) subscriber::cancel).toArray(Runnable[]::new)));
        }
    }

    /** 通知・Flow 購読と画面資源の解放をすべて試行する。失敗を集約し、複数回呼んでも再実行しない。 */
    @Override
    public final void close() {
        final SessionSubscriptions previous;
        synchronized (registrations) {
            if (!closed.compareAndSet(false, true)) return;
            previous = session;
            session = null;
        }
        CloseActions.runAll(() -> removeRegistrations(registration -> true),
                () -> { if (previous != null) previous.close(); }, this::onClose);
    }

    /** 終了処理が開始されているかを返す。非同期結果の反映前にも確認できる。 */
    public final boolean isClosed() {
        return closed.get();
    }

    /** 通知購読の解除後に実行する画面固有の終了処理。 */
    protected void onClose() {
    }

}
