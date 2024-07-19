package com.github.k7t3.tcv.app.model;

import com.github.k7t3.tcv.app.event.AppEvent;
import de.saxsys.mvvmfx.MvvmFX;
import de.saxsys.mvvmfx.ViewModel;
import de.saxsys.mvvmfx.utils.notifications.NotificationObserver;

/**
 * {@link ViewModel}がサポートする通知機能を型安全に利用できるようにしたインターフェース
 * <p>
 *     <h3>新たに以下のメソッドを使用できる。</h3>
 *     <li>{@link AppViewModel#publish(Object)}</li>
 *     <li>{@link AppViewModel#subscribe(Class, TypeSafeNotificationObserver)}</li>
 *     <li>{@link AppViewModel#unsubscribe(TypeSafeNotificationObserver)}</li>
 *     <li>{@link AppViewModel#unsubscribe(Class, TypeSafeNotificationObserver)}</li>
 * </p>
 * <p>
 *     <h3>以下のメソッドは{@link UnsupportedOperationException}をスローする。</h3>
 *     <li>{@link AppViewModel#publish(String, Object...)}</li>
 *     <li>{@link AppViewModel#subscribe(String, NotificationObserver)}</li>
 *     <li>{@link AppViewModel#unsubscribe(NotificationObserver)}</li>
 *     <li>{@link AppViewModel#unsubscribe(String, NotificationObserver)}</li>
 * </p>
 */
public interface AppViewModel extends ViewModel, UserViewModel {

    /**
     * 任意のタイプのイベントを発行する
     * @param event 発行するイベント
     * @param <T> 発行するイベントのタイプ
     */
    default <T> void publish(T event) {
        MvvmFX.getNotificationCenter().publish(event.getClass().getName(), event);
    }

    /**
     * 任意のタイプのイベントを購読する
     * @param eventType 購読するイベントのタイプ
     * @param observer イベントの購読
     * @param <T> 購読するイベントのタイプ
     */
    default <T extends AppEvent> void subscribe(Class<T> eventType, TypeSafeNotificationObserver<T> observer) {
        MvvmFX.getNotificationCenter().subscribe(eventType.getName(), observer);
    }

    /**
     * 任意のイベントハンドラの購読を取りやめる
     * @param observer イベントの購読
     * @param <T> 購読するイベントのタイプ
     */
    default <T extends AppEvent> void unsubscribe(TypeSafeNotificationObserver<T> observer) {
        MvvmFX.getNotificationCenter().unsubscribe(observer);
    }

    /**
     * 任意のイベントタイプの購読を取りやめる
     * @param eventType 購読を取りやめるイベントのタイプ
     * @param observer イベントの購読
     * @param <T> 購読するイベントのタイプ
     */
    default <T extends AppEvent> void unsubscribe(Class<T> eventType, TypeSafeNotificationObserver<T> observer) {
        MvvmFX.getNotificationCenter().unsubscribe(eventType.getName(), observer);
    }

    @Override
    @Deprecated
    default void publish(String messageName, Object... payload) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    default void subscribe(String messageName, NotificationObserver observer) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    default void unsubscribe(NotificationObserver observer) {
        throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    default void unsubscribe(String messageName, NotificationObserver observer) {
        throw new UnsupportedOperationException();
    }

}
