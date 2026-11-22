package com.github.k7t3.tcv.domain.event;

import com.github.philippheuer.events4j.api.IEventManager;
import org.junit.jupiter.api.Test;
import com.github.k7t3.tcv.domain.event.stream.StreamStateEvent;
import com.github.philippheuer.events4j.simple.SimpleEventHandler;
import com.github.twitch4j.common.util.EventManagerUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class EventSubscriptionScopeTest {
    @Test
    void realSdkManagerDeliversOnlyTheCurrentRegistrationGeneration() throws Exception {
        var received = new ConcurrentLinkedQueue<String>();
        var before = new CompletableFuture<String>();
        var current = new CompletableFuture<String>();
        try (var manager = EventManagerUtils.initializeEventManager(SimpleEventHandler.class);
             var retired = new EventSubscriptionScope();
             var replacement = new EventSubscriptionScope()) {
            retired.register(owner -> owner.subscribe(manager, StreamStateEvent.Offline.class, event -> {
                received.add("old:" + event.userId());
                before.complete(event.userId());
            }));
            manager.publish(new StreamStateEvent.Offline("42"));
            assertThat(before.get(5, TimeUnit.SECONDS)).isEqualTo("42");

            retired.close();
            replacement.register(owner -> owner.subscribe(manager, StreamStateEvent.Offline.class, event -> {
                received.add("current:" + event.userId());
                current.complete(event.userId());
            }));
            manager.publish(new StreamStateEvent.Offline("43"));
            assertThat(current.get(5, TimeUnit.SECONDS)).isEqualTo("43");
            replacement.close();
            manager.publish(new StreamStateEvent.Offline("44"));
        }

        assertThat(received).containsExactly("old:42", "current:43");
    }

    @Test
    void closedScopeDoesNotRunRegistrationCallback() {
        var scope = new EventSubscriptionScope();
        var manager = mock(IEventManager.class);
        var invoked = new AtomicBoolean();
        scope.close();

        scope.register(owner -> {
            invoked.set(true);
            owner.subscribe(manager, String.class, ignored -> { });
        });

        assertThat(invoked.get()).isFalse();
        verifyNoInteractions(manager);
    }
}
