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
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.chat.TwitchChat;
import com.github.k7t3.tcv.lifecycle.CloseActions;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class Twitch implements Closeable {

    private final TwitchClients client;
    private final Runnable releaseClient;
    private final SessionCredentialStore credentialStore;

    private TwitchAPI twitchAPI;
    private final Object lifecycleLock = new Object();
    private boolean closed;
    private final AtomicBoolean logoutStarted = new AtomicBoolean();
    private final AtomicBoolean invalidationDelivered = new AtomicBoolean();
    private volatile Runnable invalidated;

    Twitch(
            OAuth2Credential credential,
            CredentialStore credentialStore,
            TwitchClients client
    ) {
        this(credential, credentialStore, client, client::close);
    }

    Twitch(OAuth2Credential credential, CredentialStore credentialStore, TwitchClients client, Runnable releaseClient) {
        this.credentialStore = credentialStore instanceof SessionCredentialStore scope ? scope
                : new SessionCredentialStore(credentialStore, credential);
        this.client = client;
        this.releaseClient = Objects.requireNonNull(releaseClient);
        this.credentialStore.onUpdated(credential::updateCredential);
        this.credentialStore.onInvalidated(this::notifyInvalidation);
    }

    public TwitchAPI getTwitchAPI() {
        synchronized (lifecycleLock) {
            checkOpen();
            if (twitchAPI == null) twitchAPI = new TwitchAPI(this);
            return twitchAPI;
        }
    }

    CredentialStore getCredentialStore() {
        return credentialStore;
    }

    private OAuth2Credential getCredential() {
        return credentialStore.credential();
    }

    public Set<String> credentialTokens() { return credentialStore.tokens(); }
    public void freezeCredentials() { credentialStore.retire(); }
    public boolean hasInvalidCredentials() { return credentialStore.isInvalid(); }
    public boolean isClosing() { return credentialStore.isRetired(); }
    public boolean isClosed() { synchronized (lifecycleLock) { return closed; } }

    public void onInvalidated(Runnable listener) {
        invalidated = Objects.requireNonNull(listener);
        notifyInvalidation();
    }

    public void invalidateCredentials() { credentialStore.invalidate(); }

    private void notifyInvalidation() {
        var listener = invalidated;
        if (hasInvalidCredentials() && listener != null && invalidationDelivered.compareAndSet(false, true)) listener.run();
    }

    public String getAccessToken() {
        return getCredential().getAccessToken();
    }

    public String getUserId() {
        return getCredential().getUserId();
    }

    public String getUserName() {
        return getCredential().getUserName();
    }

    /**
     * ドメインパッケージでの使用に限りたい。
     */
    public TwitchClients getClient() {
        synchronized (lifecycleLock) {
            checkOpen();
            return client;
        }
    }

    public TwitchChat getChat() {
        return getClient().getChat();
    }

    public void logout() {
        if (!logoutStarted.compareAndSet(false, true)) return;
        CloseActions.runAll(this::close,
                () -> CredentialController.revokeToken(getCredential()),
                () -> credentialStore.removeCredential(getCredential()));
    }

    @Override
    public void close() {
        final TwitchAPI api;
        synchronized (lifecycleLock) {
            if (closed) return;
            closed = true;
            api = twitchAPI;
        }
        CloseActions.runAll(this::freezeCredentials, credentialStore::awaitWrites, () -> {
            if (api == null) return;
            try { api.close(); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
        }, releaseClient, () -> {
            if (hasInvalidCredentials()) credentialStore.removeCredential(getCredential());
        });
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("twitch client is closed");
    }

}
