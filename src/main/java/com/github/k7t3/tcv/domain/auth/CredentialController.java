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

package com.github.k7t3.tcv.domain.auth;

import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.authcontroller.DeviceFlowController;
import com.github.philippheuer.credentialmanager.domain.DeviceTokenResponse;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.auth.domain.TwitchScopes;
import com.github.twitch4j.auth.providers.TwitchIdentityProvider;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class CredentialController implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialController.class);

    private static final String CLIENT_ID = "m2xt95z7dxg78rys5ds3ugc7klxjwn";
    private static final String REDIRECT_URL = null;

    private final TwitchIdentityProvider identityProvider;

    private final DeviceFlowController authController;

    private final CredentialManager credentialManager;
    private final ScheduledExecutorService flowExecutor;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Void> stopRequested = new CompletableFuture<>();
    private final AtomicReference<Throwable> undeliveredPersistenceFailure = new AtomicReference<>();
    private final ThreadLocal<Integer> callbackDepth = ThreadLocal.withInitial(() -> 0);

    private volatile OAuth2Credential credential;

    public CredentialController(CredentialStore credentialStore) {
        this(credentialStore, Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory()));
    }

    private CredentialController(CredentialStore store, ScheduledExecutorService scheduler) {
        this(store, new TwitchIdentityProvider(CLIENT_ID, null, REDIRECT_URL),
                new DeviceFlowController(scheduler, 0), scheduler);
    }

    // scheduler と flow の所有権を引き受ける。
    CredentialController(CredentialStore credentialStore, TwitchIdentityProvider identityProvider,
                         DeviceFlowController flow, ScheduledExecutorService scheduler) {
        this.identityProvider = identityProvider;
        this.authController = flow;
        this.flowExecutor = scheduler;
        try {
            credentialManager = new CredentialManager(credentialStore, authController);
            credentialManager.registerIdentityProvider(identityProvider);
            if (!credentialManager.getCredentials().isEmpty()) {
                credential = (OAuth2Credential) credentialManager.getCredentials().getFirst();
            }
        } catch (RuntimeException | Error failure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                if (cleanupFailure != failure) failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    /**
     * すでに認可トークンが生成されている。ただし有効であるかどうかは判定しない。
     * @return すでに認可トークンが生成されていた場合はTrue
     */
    public boolean isAuthorized() {
        return credential != null;
    }

    /**
     * 認可トークンが生成済みだった場合、有効であるか検証する
     * @return 生成済みの認可トークンが有効であるかを判定した結果
     */
    public boolean validateToken() {
        if (!isAuthorized()) return false;
        return identityProvider.isCredentialValid(credential).orElse(false);
    }

    public OAuth2Credential refreshToken() {
        final OAuth2Credential refreshed;
        try {
            refreshed = identityProvider.refreshCredential(credential).orElse(null);
        } catch (CancellationException cancelled) {
            throw cancelled;
        } catch (RuntimeException e) {
            // リフレッシュに失敗した場合
            LOGGER.warn("failed to refresh", e);
            return null;
        }
        // 保存の失敗は認証失効と区別して返す。元の保存値を削除させない。
        if (refreshed != null) {
            credentialManager.getCredentials().clear();
            credentialManager.addCredential(identityProvider.getProviderName(), refreshed);
            credentialManager.save();
            this.credential = refreshed;
        }
        return refreshed;
    }

    /** 保存先を読み直さず、呼び出し元のセッションが所有する資格情報だけを失効する。 */
    public static void revokeToken(OAuth2Credential credential) {
        if (!new TwitchIdentityProvider(CLIENT_ID, null, REDIRECT_URL).revokeCredential(credential)) {
            throw new IllegalStateException("failed to revoke session credential");
        }
    }

    public OAuth2Credential getCredential() {
        return credential;
    }

    public CredentialManager getCredentialManager() {
        return credentialManager;
    }

    public DeviceFlow startAuthenticate(Consumer<Boolean> callback) {
        if (closed.get()) throw new IllegalStateException("credential controller is closed");
        List<Object> scopes = List.of(
                TwitchScopes.HELIX_USER_FOLLOWS_READ,
                TwitchScopes.CHAT_READ
        );
        if (credentialManager.getCredentials() != null) {
            credentialManager.getCredentials().clear();
        }
        var auth = authController.startOAuth2DeviceAuthorizationGrantType(identityProvider, scopes, r -> callback(r, callback));
        return new DeviceFlow(auth.getUserCode(), auth.getVerificationUri(), auth.getCompleteUri());
    }

    /**
     * 認証結果のコールバック
     * @param response レスポンス
     */
    private void callback(DeviceTokenResponse response, Consumer<Boolean> callback) {
        if (closed.get()) return;
        callbackDepth.set(callbackDepth.get() + 1);
        try { deliver(response, callback); }
        finally {
            var remaining = callbackDepth.get() - 1;
            if (remaining == 0) callbackDepth.remove();
            else callbackDepth.set(remaining);
        }
    }

    private void deliver(DeviceTokenResponse response, Consumer<Boolean> callback) {
        var received = response.getCredential();
        var credential = received == null ? null : credentialManager.getCredentials().stream()
                .filter(OAuth2Credential.class::isInstance).map(OAuth2Credential.class::cast)
                .filter(candidate -> java.util.Objects.equals(candidate.getIdentityProvider(), received.getIdentityProvider())
                        && java.util.Objects.equals(candidate.getAccessToken(), received.getAccessToken()))
                .findFirst().orElse(received);
        if (credential != null) {
            try { credentialManager.save(); }
            catch (Error failure) {
                retainPersistenceFailure(failure);
                throw failure;
            }
            catch (RuntimeException failure) {
                if (!closed.get()) {
                    LOGGER.warn("failed to persist authenticated credential", failure);
                    callback.accept(false);
                } else if (!(failure instanceof CancellationException)) {
                    retainPersistenceFailure(failure);
                }
                return;
            }
            if (closed.get()) return;
            this.credential = credential;
        }
        if (!closed.get()) callback.accept(credential != null);
    }

    private void retainPersistenceFailure(Throwable failure) {
        var previous = undeliveredPersistenceFailure.compareAndExchange(null, failure);
        if (previous != null && previous != failure) previous.addSuppressed(failure);
    }

    public void disposeAuthenticate() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            CloseActions.runAll(authController::close, flowExecutor::shutdownNow);
            stopRequested.complete(null);
        } catch (RuntimeException | Error failure) {
            stopRequested.completeExceptionally(failure);
            throw failure;
        }
    }

    /**
     * 停止を要求し、所有schedulerの実終了後に未通知の保存失敗も回収する。
     * 終了に伴う保存取消と通知済みの認証失敗は再送出しない。
     * UIと認証callback自身からは待たないこと。
     */
    public void awaitAuthenticationStopped() {
        if (callbackDepth.get() > 0) throw new IllegalStateException("cannot await the current authentication callback");
        CloseActions.runAll(this::disposeAuthenticate, () -> {
            try { stopRequested.join(); }
            catch (CompletionException failure) { rethrow(failure.getCause()); }
        }, flowExecutor::close, () -> {
            var failure = undeliveredPersistenceFailure.get();
            if (failure != null) rethrow(failure);
        });
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new RuntimeException(failure);
    }

    @Override
    public void close() {
        disposeAuthenticate();
    }

    public record DeviceFlow(String userCode, String verificationURL, String completeURL) {}

}
