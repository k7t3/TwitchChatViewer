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

package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.auth.CredentialController;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.domain.auth.SessionCredentialStore;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class TwitchLoader implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchLoader.class);

    public record DeviceFlow(String userCode, String verificationURL, String completeURL) {}
    private final CredentialController controller;
    private final Function<OAuth2Credential, Twitch> clientFactory;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean controllerReleased = new AtomicBoolean();
    private final CompletableFuture<Void> controllerReleaseCompleted = new CompletableFuture<>();
    private final SessionCredentialStore scope;
    private final Object ownership = new Object();
    private boolean transferred;
    private int activeCalls;
    private Throwable cleanupFailure;
    private final ThreadLocal<Integer> callDepth = ThreadLocal.withInitial(() -> 0);

    public TwitchLoader(CredentialStore credentialStore) {
        this(new SessionCredentialStore(credentialStore));
    }

    private TwitchLoader(SessionCredentialStore scope) {
        this(scope, new CredentialController(scope));
    }

    private TwitchLoader(SessionCredentialStore scope, CredentialController controller) {
        this(controller, credential -> createAppClient(scope, controller, credential), scope);
    }

    TwitchLoader(CredentialController controller, Function<OAuth2Credential, Twitch> clientFactory) {
        this(controller, clientFactory, null);
    }

    private TwitchLoader(CredentialController controller, Function<OAuth2Credential, Twitch> clientFactory,
                         SessionCredentialStore scope) {
        this.controller = controller;
        this.clientFactory = clientFactory;
        this.scope = scope;
    }

    public DeviceFlow startAuthenticate(Consumer<Optional<Twitch>> consumer) {
        if (!enterCall()) throw new IllegalStateException("credential loader is closed");
        try { return startOwnedAuthentication(consumer); }
        finally { leaveCall(); }
    }

    private DeviceFlow startOwnedAuthentication(Consumer<Optional<Twitch>> consumer) {
        var flow = controller.startAuthenticate(result -> {
            if (!enterCall()) return;
            try {
                if (result) {
                    final Twitch twitch;
                    try {
                        twitch = clientFactory.apply(controller.getCredential());
                    } catch (RuntimeException failure) {
                        LOGGER.warn("failed to create authenticated client", failure);
                        if (!closed.get()) consumer.accept(Optional.empty());
                        return;
                    }
                    if (!transfer()) {
                        closeUnacceptedClient(twitch);
                        return;
                    }
                    consumer.accept(Optional.of(twitch));
                } else {
                    consumer.accept(Optional.empty());
                }
            } finally {
                try { releaseController(); }
                finally { leaveCall(); }
            }
        });
        return new DeviceFlow(flow.userCode(), flow.verificationURL(), flow.completeURL());
    }

    public Optional<Twitch> load() {
        if (!enterCall()) throw new IllegalStateException("credential loader is closed");
        try { return loadOwnedClient(); }
        finally { leaveCall(); }
    }

    private Optional<Twitch> loadOwnedClient() {
        if (closed.get()) throw new IllegalStateException("credential loader is closed");
        if (!controller.isAuthorized()) {
            return Optional.empty();
        }

        OAuth2Credential credential;

        // 資格情報を検証して適宜リフレッシュする
        if (controller.validateToken()) {

            // 有効だった場合はその資格情報を使う
            credential = (OAuth2Credential) controller.getCredentialManager()
                    .getCredentials().getFirst();

        } else {

            // 無効だった場合はリフレッシュする
            LOGGER.info("refresh a token");
            credential = controller.refreshToken();

            // リフレッシュに失敗したときは資格情報を空で返す
            if (credential == null) {
                LOGGER.info("failed to refresh token");
                return Optional.empty();
            } else {
                LOGGER.info("refreshed access token");
            }
        }

        var twitch = clientFactory.apply(credential);
        if (transfer()) return Optional.of(twitch);
        closeUnacceptedClient(twitch);
        return Optional.empty();
    }

    private static Twitch createAppClient(SessionCredentialStore credentialStore, CredentialController controller,
                                         OAuth2Credential credential) {
        credential = controller.resolveUserIdentity(credential);
        credentialStore.bind(credential);
        var credentialManager = controller.getCredentialManager();
        credentialManager.getCredentials().clear();
        credentialManager.getCredentials().add(credential);
        TwitchClientFactory.Resources resources = null;
        try {
            resources = TwitchClientFactory.create(credentialManager, credential, credentialStore::isRetired);
            var owned = resources;
            return new Twitch(credential, credentialStore, owned.client(), owned::close);
        } catch (RuntimeException | Error failure) {
            var owned = resources;
            CloseActions.runAll(() -> {
                if (failure instanceof RuntimeException exception) throw exception;
                throw (Error) failure;
            }, credentialStore::retire, () -> { if (owned != null) owned.close(); });
            throw failure;
        }
    }

    private boolean transfer() {
        synchronized (ownership) {
            if (closed.get()) return false;
            transferred = true;
            return true;
        }
    }

    @Override
    public void close() {
        synchronized (ownership) {
            closed.set(true);
            if (!transferred && scope != null) scope.retire();
        }
        releaseController();
    }

    private void releaseController() {
        if (!controllerReleased.compareAndSet(false, true)) return;
        try {
            controller.close();
            controllerReleaseCompleted.complete(null);
        } catch (RuntimeException | Error failure) {
            controllerReleaseCompleted.completeExceptionally(failure);
            throw failure;
        }
    }

    /** UI の close 要求とは別に、認証 I/O・callback・未採用 client の解放まで背景で待つ。 */
    public void awaitClosed() {
        if (callDepth.get() > 0) throw new IllegalStateException("cannot await the current credential operation");
        CloseActions.runAll(this::close, () -> {
            try { controllerReleaseCompleted.join(); }
            catch (CompletionException failure) { rethrow(failure.getCause()); }
        }, controller::awaitAuthenticationStopped, this::awaitCalls, () -> {
            synchronized (ownership) { if (cleanupFailure != null) rethrow(cleanupFailure); }
        });
    }

    private void closeUnacceptedClient(Twitch client) {
        try { client.close(); }
        catch (RuntimeException | Error failure) {
            synchronized (ownership) {
                if (cleanupFailure == null) cleanupFailure = failure;
                else if (cleanupFailure != failure) cleanupFailure.addSuppressed(failure);
            }
            throw failure;
        }
    }

    private boolean enterCall() {
        synchronized (ownership) {
            if (closed.get()) return false;
            activeCalls++;
            callDepth.set(callDepth.get() + 1);
            return true;
        }
    }

    private void leaveCall() {
        synchronized (ownership) {
            activeCalls--;
            var remaining = callDepth.get() - 1;
            if (remaining == 0) callDepth.remove();
            else callDepth.set(remaining);
            ownership.notifyAll();
        }
    }

    private void awaitCalls() {
        boolean interrupted = false;
        try {
            synchronized (ownership) {
                while (activeCalls > 0) {
                    try { ownership.wait(); }
                    catch (InterruptedException ignored) { interrupted = true; }
                }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new RuntimeException(failure);
    }

}
