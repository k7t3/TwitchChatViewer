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

package com.github.k7t3.tcv.app.auth;

import com.github.k7t3.tcv.app.secure.CipherOperator;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.philippheuer.credentialmanager.domain.Credential;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

public class PreferencesCredentialStorage implements CredentialStore {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreferencesCredentialStorage.class);

    private final String PREFERENCES_KEY = "credentials";

    private static final String NULL_STRING = "NULL";

    private final List<Credential> credentials = new ArrayList<>();

    private final Preferences preferences;

    public PreferencesCredentialStorage(Preferences preferences) {
        this.preferences = preferences;
    }

    private void loadOAuthCredentials() {
        if (!credentials.isEmpty()) return;

        var bytes = preferences.getByteArray(PREFERENCES_KEY, null);
        if (bytes == null)
            return;

        // トークン情報を復号する
        bytes = CipherOperator.decrypt(bytes);

        try (var bais = new ByteArrayInputStream(bytes);
             var is = new DataInputStream(bais)) {

            while (0 < is.available()) {
                var identityProvider = is.readUTF();
                var accessToken = is.readUTF();
                var refreshToken = is.readUTF();
                var userId = is.readUTF();
                var userName = is.readUTF();
                var expireIn = is.readInt();

                var scopeCount = is.readInt();
                var scopes = new ArrayList<String>(scopeCount);
                for (var i = 0; i < scopeCount; i++) {
                    scopes.add(is.readUTF());
                }

                var contextCount = is.readInt();
                var context = new HashMap<String, Object>(contextCount);
                for (var i = 0; i < contextCount; i++) {
                    var entry = is.readUTF();
                    var split = entry.split("=", 2);
                    context.put(split[0], split[1]);
                }

                var receiveAt = Instant.ofEpochMilli(is.readLong());

                var credential = new OAuth2Credential(
                        identityProvider,
                        accessToken,
                        refreshToken.equals(NULL_STRING) ? null : refreshToken,
                        userId.equals(NULL_STRING) ? null : userId,
                        userName.equals(NULL_STRING) ? null : userName,
                        expireIn,
                        scopes,
                        context
                );
                credential.setReceivedAt(receiveAt);

                credentials.add(credential);

                LOGGER.info("loaded credential");
            }

        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public List<Credential> loadCredentials() {
        loadOAuthCredentials();
        return new ArrayList<>(credentials);
    }

    private void saveOAuthCredentials() {
        try (var baos = new ByteArrayOutputStream();
             var os = new DataOutputStream(baos)) {

            LOGGER.info("saved credential");

            for (var credential : credentials) {
                if (credential instanceof OAuth2Credential c) {
                    os.writeUTF(c.getIdentityProvider());
                    os.writeUTF(c.getAccessToken());
                    os.writeUTF(c.getRefreshToken() == null ? NULL_STRING : c.getRefreshToken());
                    os.writeUTF(c.getUserId() == null ? NULL_STRING : c.getUserId());
                    os.writeUTF(c.getUserName() == null ? NULL_STRING : c.getUserName());
                    os.writeInt(c.getExpiresIn());

                    // scope
                    os.writeInt(c.getScopes().size());
                    for (var scope : c.getScopes()) {
                        os.writeUTF(scope);
                    }

                    // コンテキスト
                    os.writeInt(c.getContext().size());
                    for (var entry : c.getContext().entrySet()) {
                        var v = entry.getKey() + "=" + entry.getValue();
                        os.writeUTF(v);
                    }

                    var epoch = c.getReceivedAt().toEpochMilli();
                    os.writeLong(epoch);
                }
            }

            os.flush();

            // トークン情報を暗号化
            var bytes = CipherOperator.encrypt(baos.toByteArray());

            preferences.putByteArray(PREFERENCES_KEY, bytes);

        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public void saveCredentials(List<Credential> credentials) {
        this.credentials.clear();
        this.credentials.addAll(credentials);
        saveOAuthCredentials();
    }

    @Override
    public Optional<Credential> getCredentialByUserId(String userId) {
        for (var credential : credentials) {
            if (credential.getUserId().equalsIgnoreCase(userId)) {
                return Optional.of(credential);
            }
        }
        return Optional.empty();
    }

    @Override
    public void clearCredentials() {
        preferences.remove(PREFERENCES_KEY);
        this.credentials.clear();
    }

}
