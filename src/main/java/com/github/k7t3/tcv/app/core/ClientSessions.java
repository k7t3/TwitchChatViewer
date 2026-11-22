package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import javafx.beans.property.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * アプリごとの現在・終了待ちのセッションと、資格情報の再採用制御を所有する。
 * executorは借用し、停止しない。UIを切り離した後、実I/Oの回収後に背景でcloseする。
 */
public final class ClientSessions implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSessions.class);
    private final Object lifecycleLock = new Object();
    private final Executor executor;
    private final Executor ui;
    private final Function<Twitch, ChannelRepository> channelFactory;
    private final Runnable invalidated;
    private final ReadOnlyStringWrapper userId = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper();
    private final ReadOnlyObjectWrapper<Twitch> twitch = new ReadOnlyObjectWrapper<>();
    private final Set<ClientSession> sessions = new HashSet<>();
    private final Set<String> revokedTokens = new HashSet<>();
    private final Set<ClientSession> revokingSessions = new HashSet<>();
    private final List<Throwable> sessionFailures = new ArrayList<>();
    private final CompletableFuture<Void> completed = new CompletableFuture<>();
    private ClientSession activeSession;
    private boolean stopping;
    private boolean closed;
    private Thread closingThread;

    ClientSessions(Executor executor, Executor ui, Function<Twitch, ChannelRepository> channelFactory, Runnable invalidated) {
        this.executor = Objects.requireNonNull(executor);
        this.ui = Objects.requireNonNull(ui);
        this.channelFactory = Objects.requireNonNull(channelFactory);
        this.invalidated = Objects.requireNonNull(invalidated);
        userId.bind(twitch.map(Twitch::getUserId));
        userName.bind(twitch.map(Twitch::getUserName));
        authorized.bind(twitch.isNotNull());
    }

    /** 現在のセッションのリポジトリを借用する。終了後・未ログインでは取得できない。 */
    public ChannelRepository getChannelRepository() {
        synchronized (lifecycleLock) {
            checkOpen();
            if (activeSession == null) throw new IllegalStateException("no active client session");
            return activeSession.channels();
        }
    }

    /** FXで画面を切り離した後に背景で解放する。観測の取消は実解放を取り消さない。 */
    public CompletionStage<Void> logoutAsync(Runnable detachViews) {
        return endSession(null, true, detachViews);
    }

    private CompletionStage<Void> endSession(ClientSession expected, boolean revoke, Runnable detachViews) {
        final ClientSession session;
        var detached = new CompletableFuture<Void>();
        Throwable preparationFailure = null;
        synchronized (lifecycleLock) {
            if (expected != null && (closed || stopping || activeSession != expected)) return CompletableFuture.completedFuture(null);
            checkOpen();
            session = activeSession;
            if (session != null) {
                try {
                    CloseActions.runAll(() -> {
                        if (revoke) session.requestLogout(detached);
                        else session.requestClose(detached);
                    }, () -> {
                        revokingSessions.add(session);
                        revokedTokens.addAll(tokens(session.twitch()));
                    });
                } catch (RuntimeException | Error failure) {
                    preparationFailure = failure;
                }
            }
            activeSession = null;
        }
        Throwable detachFailure = null;
        final var beforeDetach = preparationFailure;
        try {
            CloseActions.runAll(() -> { if (beforeDetach != null) rethrow(beforeDetach); },
                    () -> twitch.set(null), detachViews);
        } catch (RuntimeException | Error failure) {
            detachFailure = failure;
        }
        if (detachFailure == null) detached.complete(null);
        else detached.completeExceptionally(detachFailure);
        var result = new CompletableFuture<Void>();
        final var previous = detachFailure;
        var completion = session == null ? CompletableFuture.<Void>completedFuture(null) : session.completion();
        completion.whenComplete((ignored, failure) -> {
            var actual = unwrap(failure);
            if (previous != null) {
                if (actual != null && actual != previous) previous.addSuppressed(actual);
                actual = previous;
            }
            if (actual == null) result.complete(null);
            else result.completeExceptionally(actual);
        });
        if (session != null) releaseAsync(session);
        return result.minimalCompletionStage();
    }

    private void sessionInvalidated(ClientSession session) {
        ui.execute(() -> endSession(session, false, invalidated));
    }

    private static Set<String> tokens(Twitch client) {
        var result = new HashSet<>(client.credentialTokens());
        var current = client.getAccessToken();
        if (current != null) result.add(current);
        return result;
    }

    private void releaseAsync(ClientSession session) {
        Runnable release = () -> {
            try { session.close(); }
            catch (RuntimeException | Error failure) { LOGGER.error("client session release failed", failure); }
        };
        try {
            executor.execute(release);
        } catch (RuntimeException | Error rejected) {
            // sessions が引き続き所有するため、アプリ停止もこの終了完了を回収する。
            Thread.ofVirtual().name("TCV-Session-End").start(release);
        }
    }

    ReadOnlyStringProperty userIdProperty() { return userId.getReadOnlyProperty(); }
    String getUserId() { return userId.get(); }

    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    String getUserName() { return userName.get(); }

    public ReadOnlyBooleanProperty authorizedProperty() { return authorized.getReadOnlyProperty(); }
    boolean isAuthorized() { return authorized.get(); }

    ReadOnlyObjectProperty<Twitch> twitchProperty() { return twitch.getReadOnlyProperty(); }
    public Twitch getTwitch() { return twitch.get(); }
    /** FX上でクライアントを採用する。終了・失効済みの資格情報は受け付けない。 */
    public void adopt(Twitch client) {
        final ClientSession previous;
        synchronized (lifecycleLock) {
            checkOpen();
            if (activeSession != null && activeSession.twitch() == client) return;
            if (client != null) {
                var candidate = tokens(client);
                if (client.isClosed() || client.isClosing() || client.hasInvalidCredentials()
                        || candidate.stream().anyMatch(revokedTokens::contains)
                        || revokingSessions.stream().anyMatch(session -> tokens(session.twitch()).stream().anyMatch(candidate::contains))) {
                    throw new IllegalStateException("client credential belongs to an ended session");
                }
            }
            var next = client == null ? null : new ClientSession(client, channelFactory.apply(client));
            previous = activeSession;
            activeSession = next;
            if (next != null) {
                sessions.add(next);
                next.completion().whenComplete((ignored, failure) -> {
                    synchronized (lifecycleLock) {
                        sessions.remove(next);
                        if (revokingSessions.remove(next)) revokedTokens.addAll(tokens(next.twitch()));
                        if (failure != null) sessionFailures.add(unwrap(failure));
                    }
                });
                client.onInvalidated(() -> sessionInvalidated(next));
            }
        }
        CloseActions.runAll(() -> twitch.set(client), () -> { if (previous != null) releaseAsync(previous); });
    }

    /** 新しい採用を止める。状態確定中にクライアント側の処理は呼び出さない。 */
    void stopAccepting() {
        synchronized (lifecycleLock) {
            stopping = true;
        }
    }

    /** 停止済みの受付範囲にある全クライアントを試行し、最初の失敗を保持する。 */
    void freezeCredentials() {
        final List<ClientSession> owned;
        synchronized (lifecycleLock) { owned = List.copyOf(sessions); }
        CloseActions.runAll(owned.stream().map(session -> (Runnable) session.twitch()::freezeCredentials).toArray(Runnable[]::new));
    }

    /** 所有するプロパティの参照をFX側の終了準備で外す。 */
    void detachViews() { twitch.set(null); }

    @Override
    public void close() {
        final List<ClientSession> owned;
        final List<Throwable> failures;
        final boolean owner;
        synchronized (lifecycleLock) {
            if (closingThread == Thread.currentThread()) return;
            owner = !closed;
            if (owner) {
                closed = true;
                stopping = true;
                closingThread = Thread.currentThread();
            }
            owned = List.copyOf(sessions);
            failures = List.copyOf(sessionFailures);
        }
        if (!owner) {
            try { completed.join(); }
            catch (CompletionException failure) { rethrow(unwrap(failure)); }
            return;
        }
        try {
            CloseActions.runAll(
                    () -> CloseActions.runAll(owned.stream().map(session -> (Runnable) session::close).toArray(Runnable[]::new)),
                    () -> CloseActions.runAll(failures.stream().map(failure -> (Runnable) () -> rethrow(failure)).toArray(Runnable[]::new)));
            completed.complete(null);
        } catch (RuntimeException | Error failure) {
            completed.completeExceptionally(failure);
            throw failure;
        } finally {
            synchronized (lifecycleLock) { closingThread = null; }
        }
    }

    private void checkOpen() {
        if (closed || stopping) throw new IllegalStateException("application is closed");
    }

    private static Throwable unwrap(Throwable failure) {
        while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
        return failure;
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new IllegalStateException(failure);
    }
}
