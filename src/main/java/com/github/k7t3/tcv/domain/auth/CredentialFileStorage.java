package com.github.k7t3.tcv.domain.auth;

import com.github.philippheuer.credentialmanager.api.IStorageBackend;
import com.github.philippheuer.credentialmanager.domain.Credential;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

public class CredentialFileStorage implements IStorageBackend {

    private static final Logger LOGGER = LoggerFactory.getLogger(CredentialFileStorage.class);

    private static final String NULL_STRING = "NULL";

    private final List<Credential> credentials = new ArrayList<>();

    private final Path credentialPath;

    public CredentialFileStorage(Path credentialPath) {
        this.credentialPath = Objects.requireNonNull(credentialPath);
    }

    private void loadOAuthCredentials() {
        this.credentials.clear();
        if (!Files.exists(credentialPath)) {
            return;
        }

        try (var is = new DataInputStream(Files.newInputStream(credentialPath))) {

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

                LOGGER.info("loaded credential {}", credential);
            }

        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public List<Credential> loadCredentials() {
        LOGGER.info("load credentials");
        loadOAuthCredentials();
        return new ArrayList<>(credentials);
    }

    private void saveOAuthCredentials() {
        try (var os = new DataOutputStream(Files.newOutputStream(credentialPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {

            LOGGER.info("save credentials {}", credentials);

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

                    LOGGER.info("saved credential {}", credential);
                }
            }

        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    public void saveCredentials(List<Credential> credentials) {
        LOGGER.info("save all credentials");
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
}
