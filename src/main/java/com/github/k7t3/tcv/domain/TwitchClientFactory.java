package com.github.k7t3.tcv.domain;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.chat.TwitchChatBuilder;
import com.github.twitch4j.common.config.Twitch4JGlobal;
import com.github.twitch4j.common.util.EventManagerUtils;
import com.github.philippheuer.events4j.simple.SimpleEventHandler;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.lifecycle.OwnedScheduler;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

final class TwitchClientFactory {
    record Context(CredentialManager credentials, OAuth2Credential token, EventManager events, ScheduledThreadPoolExecutor scheduler) {}
    static final class Resources implements AutoCloseable {
        private final TwitchClients client;
        private final Context context;
        private final HelixClientFactory.Handle http;
        private final TwitchChat chat;
        private final OwnedScheduler scheduler;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CompletableFuture<Void> moduleCloseFinished = new CompletableFuture<>();
        private volatile Thread releasingThread;
        Resources(Context context, HelixClientFactory.Handle http, TwitchChat chat, OwnedScheduler scheduler) {
            this.context = context; this.http = http; this.chat = chat;
            this.scheduler = scheduler;
            this.client = new TwitchClients(context.events(), http.client(), chat, this::close);
        }
        TwitchClients client() { return client; }
        @Override public void close() {
            if (!closed.compareAndSet(false, true)) {
                if (releasingThread == Thread.currentThread() || scheduler.isWorkerThread()
                        || http.isProcessingOnCurrentThread()) return;
                moduleCloseFinished.join();
                CloseActions.runAll(http::awaitClosed, scheduler::close);
                return;
            }
            releasingThread = Thread.currentThread();
            try {
                CloseActions.runAll(http::close, chat::close,
                        context.events()::close, scheduler::close);
            } finally {
                moduleCloseFinished.complete(null);
                releasingThread = null;
            }
        }
    }

    private final Supplier<ScheduledThreadPoolExecutor> schedulers;
    private final Supplier<EventManager> events;
    private final Function<Context, HelixClientFactory.Handle> http;
    private final Function<Context, TwitchChat> chat;

    TwitchClientFactory(Supplier<ScheduledThreadPoolExecutor> schedulers, Supplier<EventManager> events,
                        Function<Context, HelixClientFactory.Handle> http, Function<Context, TwitchChat> chat) {
        this.schedulers = Objects.requireNonNull(schedulers); this.events = Objects.requireNonNull(events);
        this.http = Objects.requireNonNull(http); this.chat = Objects.requireNonNull(chat);
    }

    static Resources create(CredentialManager credentials, OAuth2Credential token) {
        return create(credentials, token, () -> false);
    }

    static Resources create(CredentialManager credentials, OAuth2Credential token, BooleanSupplier cancelled) {
        var options = new HelixClientFactory.Options(Twitch4JGlobal.clientId, Twitch4JGlobal.clientSecret,
                HelixClientFactory.OFFICIAL_BASE_URL, 5000);
        var factory = new TwitchClientFactory(() -> {
            var pool = new ScheduledThreadPoolExecutor(3, Thread.ofPlatform().daemon().name("tcv-twitch-",0).factory());
            pool.setRemoveOnCancelPolicy(true);
            pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            return pool;
        }, () -> EventManagerUtils.initializeEventManager(SimpleEventHandler.class),
                context -> HelixClientFactory.create(context.credentials(), context.token(), context.scheduler(), options),
                context -> TwitchChatBuilder.builder().withCredentialManager(context.credentials()).withChatAccount(context.token())
                        .withEventManager(context.events()).withScheduledThreadPoolExecutor(context.scheduler()).build());
        return factory.open(credentials, token, cancelled);
    }

    Resources open(CredentialManager credentials, OAuth2Credential token) {
        return open(credentials, token, () -> false);
    }

    Resources open(CredentialManager credentials, OAuth2Credential token, BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        var pool = Objects.requireNonNull(schedulers.get());
        var ownedScheduler = new OwnedScheduler(pool);
        EventManager eventManager = null;
        HelixClientFactory.Handle transport = null;
        TwitchChat chatClient = null;
        try {
            checkCancelled(cancelled);
            eventManager = Objects.requireNonNull(events.get());
            checkCancelled(cancelled);
            var context = new Context(credentials, token, eventManager, pool);
            transport = Objects.requireNonNull(http.apply(context));
            checkCancelled(cancelled);
            chatClient = Objects.requireNonNull(chat.apply(context));
            checkCancelled(cancelled);
            var resources = new Resources(context, transport, chatClient, ownedScheduler);
            checkCancelled(cancelled);
            return resources;
        } catch (RuntimeException | Error failure) {
            var ownedEvents = eventManager; var ownedHttp = transport; var ownedChat = chatClient;
            CloseActions.runAll(() -> {
                if (failure instanceof RuntimeException exception) throw exception;
                throw (Error) failure;
            },
                    () -> { if (ownedChat != null) ownedChat.close(); },
                    () -> { if (ownedHttp != null) ownedHttp.close(); },
                    () -> { if (ownedEvents != null) ownedEvents.close(); }, ownedScheduler::close);
            throw failure;
        }
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean())
            throw new CancellationException("Client construction has ended");
    }
}
