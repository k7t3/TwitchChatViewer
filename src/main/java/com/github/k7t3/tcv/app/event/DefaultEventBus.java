/* SPDX-License-Identifier: MIT */

package com.github.k7t3.tcv.app.event;

import javafx.application.Platform;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

/**
 * Simple event bus implementation.
 *
 * <p>Subscribe and publish events. Events are published in channels distinguished by event type.
 * Channels can be grouped using an event type hierarchy.
 *
 * <p>You can use the default event bus instance {@link #getInstance}, which is a singleton,
 * or you can create one or multiple instances of {@link DefaultEventBus}.
 * <p>
 * <a href="https://github.com/mkpaz/atlantafx/blob/master/sampler/src/main/java/atlantafx/sampler/event/DefaultEventBus.java">参考</a>
 */
@SuppressWarnings({"unchecked", "rawtypes"})
final class DefaultEventBus implements EventBus {

    public DefaultEventBus() {
    }

    private final Map<Class<?>, Set<Consumer>> subscribers = new ConcurrentHashMap<>();

    @Override
    public <E extends Event> void subscribe(Class<? extends E> eventType, Consumer<E> subscriber) {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(subscriber);

        Set<Consumer> eventSubscribers = getOrCreateSubscribers(eventType);
        eventSubscribers.add(subscriber);
    }

    private <E> Set<Consumer> getOrCreateSubscribers(Class<E> eventType) {
        return subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArraySet<>());
    }

    @Override
    public <E extends Event> void unsubscribe(Consumer<E> subscriber) {
        Objects.requireNonNull(subscriber);

        subscribers.values().forEach(eventSubscribers -> eventSubscribers.remove(subscriber));
    }

    @Override
    public <E extends Event> void unsubscribe(Class<? extends E> eventType, Consumer<E> subscriber) {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(subscriber);

        subscribers.keySet().stream()
            .filter(eventType::isAssignableFrom)
            .map(subscribers::get)
            .forEach(eventSubscribers -> eventSubscribers.remove(subscriber));
    }

    @Override
    public <E extends Event> void publish(E event) {
        if (Platform.isFxApplicationThread()) {
            publishImpl(event);
        } else {
            Platform.runLater(() -> publishImpl(event));
        }
    }

    public <E extends Event> void publishImpl(E event) {
        Objects.requireNonNull(event);

        Class<?> eventType = event.getClass();
        subscribers.keySet().stream()
                .filter(type -> type.isAssignableFrom(eventType))
                .flatMap(type -> subscribers.get(type).stream())
                .forEach(subscriber -> publish(event, subscriber));
    }

    private <E extends Event> void publish(E event, Consumer<E> subscriber) {
        try {
            subscriber.accept(event);
        } catch (Exception e) {
            Thread.currentThread().getUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
        }
    }

    ///////////////////////////////////////////////////////////////////////////

    private static class InstanceHolder {

        private static final DefaultEventBus INSTANCE = new DefaultEventBus();
    }

    public static DefaultEventBus getInstance() {
        return InstanceHolder.INSTANCE;
    }
}
