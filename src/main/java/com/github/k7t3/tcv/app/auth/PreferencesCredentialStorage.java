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

import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.io.PreferenceFileLock;
import com.github.philippheuer.credentialmanager.domain.Credential;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PreferencesCredentialStorage implements CredentialStore {
    private static final String PREFERENCES_KEY = "credentials";
    private static final String NULL_STRING = "NULL";
    private final Preferences preferences;
    private final UnaryOperator<byte[]> encrypt;
    private final UnaryOperator<byte[]> decrypt;
    private final Path lockFile;
    private final Path coordinationFile;

    /** 呼出側が永続化を所有する借用ノード。排他は同じJVMのノード内に限る。 */
    public PreferencesCredentialStorage(Preferences preferences, UnaryOperator<byte[]> encrypt, UnaryOperator<byte[]> decrypt) {
        this(preferences, encrypt, decrypt, null);
    }

    /**
     * 比較更新とflushをプロセス間で直列化する。構築時のI/Oは行わない。
     * @param preferences 同じlockFileを使う全プロセスが共有する保存ノード
     * @param encrypt 暗号化処理
     * @param decrypt 復号処理
     * @param lockFile 移動するDBとは独立した共有ロック。nullは借用ノード用のJVM内排他
     */
    public PreferencesCredentialStorage(Preferences preferences, UnaryOperator<byte[]> encrypt,
                                        UnaryOperator<byte[]> decrypt, Path lockFile) {
        this(preferences, encrypt, decrypt, lockFile, null);
    }

    /**
     * 設定と共有する排他を先に取得し、既存の資格情報専用排他も維持する。構築時のI/Oは行わない。
     * @param preferences 設定エディターと共有する保存ノード
     * @param encrypt 暗号化処理
     * @param decrypt 復号処理
     * @param lockFile 既存の資格情報専用ロック。nullは専用排他なし
     * @param coordinationFile 設定保存と共有するロック。nullは従来の専用排他だけを使う
     */
    public PreferencesCredentialStorage(Preferences preferences, UnaryOperator<byte[]> encrypt,
                                        UnaryOperator<byte[]> decrypt, Path lockFile, Path coordinationFile) {
        this.preferences = Objects.requireNonNull(preferences);
        this.encrypt = Objects.requireNonNull(encrypt);
        this.decrypt = Objects.requireNonNull(decrypt);
        this.lockFile = lockFile == null ? null : lockFile.toAbsolutePath().normalize();
        this.coordinationFile = coordinationFile == null ? null : coordinationFile.toAbsolutePath().normalize();
    }

    private <T> T exclusive(Supplier<T> operation) {
        if (lockFile == null && coordinationFile == null) {
            synchronized (preferences) { return operation.get(); }
        }
        return PreferenceFileLock.exclusive(coordinationFile, () -> PreferenceFileLock.exclusive(lockFile, () -> {
            synchronized (preferences) {
                syncOwnedNode();
                return operation.get();
            }
        }));
    }

    private void syncOwnedNode() {
        try { preferences.sync(); }
        catch (BackingStoreException failure) { throw new IllegalStateException("failed to synchronize credentials", failure); }
    }

    private void flushOwnedNode() {
        if (lockFile == null && coordinationFile == null) return;
        try { preferences.flush(); }
        catch (BackingStoreException failure) { throw new IllegalStateException("failed to flush credentials", failure); }
    }

    private void publish(byte[] next) {
        var owned = lockFile != null || coordinationFile != null;
        var previous = owned ? preferences.getByteArray(PREFERENCES_KEY, null) : null;
        try {
            replaceRaw(next);
            flushOwnedNode();
        } catch (RuntimeException | Error failure) {
            if (owned) {
                try { replaceRaw(previous);flushOwnedNode(); }
                catch (RuntimeException | Error compensation) {
                    if (compensation != failure) failure.addSuppressed(compensation);
                }
            }
            throw failure;
        }
    }

    private void replaceRaw(byte[] value) {
        if (value == null) preferences.remove(PREFERENCES_KEY);
        else preferences.putByteArray(PREFERENCES_KEY, value);
    }

    /** 所有ノードは共有ロック内でsyncし、現在の資格情報を読む。 */
    @Override
    public List<Credential> loadCredentials() {
        return exclusive(this::readCredentials);
    }

    private List<Credential> readCredentials() {
        return decode(preferences.getByteArray(PREFERENCES_KEY, null));
    }

    private List<Credential> decode(byte[] encrypted) {
        var credentials = new ArrayList<Credential>();
        if (encrypted == null) return credentials;
        try (var input = new DataInputStream(new ByteArrayInputStream(decrypt.apply(encrypted)))) {
            while (input.available() > 0) {
                var provider = input.readUTF();
                var accessToken = input.readUTF();
                var refreshToken = input.readUTF();
                var userId = input.readUTF();
                var userName = input.readUTF();
                var expiresIn = input.readInt();
                var scopeCount = readCount(input);
                var scopes = new ArrayList<String>(scopeCount);
                for (var i = 0; i < scopeCount; i++) scopes.add(input.readUTF());
                var contextCount = readCount(input);
                var context = new HashMap<String, Object>();
                for (var i = 0; i < contextCount; i++) {
                    var entry = input.readUTF().split("=", 2);
                    if (entry.length != 2) throw new IOException("invalid credential context");
                    context.put(entry[0], entry[1]);
                }
                var receivedAt = Instant.ofEpochMilli(input.readLong());
                var credential = new OAuth2Credential(provider, accessToken, nullable(refreshToken), nullable(userId),
                        nullable(userName), expiresIn, scopes, context);
                credential.setReceivedAt(receivedAt);
                credentials.add(credential);
            }
            return credentials;
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read stored credentials", failure);
        }
    }

    private static int readCount(DataInputStream input) throws IOException {
        var count = input.readInt();
        if (count < 0 || count > input.available() / 2) throw new IOException("invalid credential entry count");
        return count;
    }

    private static String nullable(String value) {
        return NULL_STRING.equals(value) ? null : value;
    }

    private static String serialized(String value) {
        return value == null ? NULL_STRING : value;
    }

    @Override
    public void saveCredentials(List<Credential> credentials) {
        exclusive(() -> { writeCredentials(credentials);return null; });
    }

    private void writeCredentials(List<Credential> credentials) {
        publish(encode(credentials));
    }

    private byte[] encode(List<Credential> credentials) {
        try (var bytes = new ByteArrayOutputStream(); var output = new DataOutputStream(bytes)) {
            for (var value : credentials) {
                if (!(value instanceof OAuth2Credential credential)) continue;
                output.writeUTF(credential.getIdentityProvider());
                output.writeUTF(credential.getAccessToken());
                output.writeUTF(serialized(credential.getRefreshToken()));
                output.writeUTF(serialized(credential.getUserId()));
                output.writeUTF(serialized(credential.getUserName()));
                output.writeInt(credential.getExpiresIn());
                output.writeInt(credential.getScopes().size());
                for (var scope : credential.getScopes()) output.writeUTF(scope);
                output.writeInt(credential.getContext().size());
                for (var entry : credential.getContext().entrySet()) output.writeUTF(entry.getKey() + "=" + entry.getValue());
                output.writeLong(credential.getReceivedAt().toEpochMilli());
            }
            output.flush();
            return encrypt.apply(bytes.toByteArray());
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to save credentials", failure);
        }
    }

    @Override
    public Snapshot snapshot() {
        return exclusive(() -> {
            var encrypted = preferences.getByteArray(PREFERENCES_KEY, null);
            return new Snapshot(revision(encrypted), decode(encrypted));
        });
    }

    @Override
    public Optional<Snapshot> compareAndSet(Snapshot expected, List<Credential> replacement, BooleanSupplier permitted) {
        return exclusive(() -> {
            var current = preferences.getByteArray(PREFERENCES_KEY, null);
            if (!Objects.equals(expected.revision(), revision(current))) return Optional.empty();
            // 空の一覧も暗号化して保存し、削除前の空状態と版を区別する。
            var encrypted = encode(replacement);
            if (!permitted.getAsBoolean()) return Optional.empty();
            var result = new Snapshot(revision(encrypted), decode(encrypted));
            // 許可判定や暗号処理から再入した変更も、古い比較で上書きしない。
            if (!Arrays.equals(current, preferences.getByteArray(PREFERENCES_KEY, null))) return Optional.empty();
            publish(encrypted);
            return Optional.of(result);
        });
    }

    private static String revision(byte[] encrypted) {
        if (encrypted == null) return "";
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encrypted)); }
        catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }

    @Override
    public Optional<Credential> getCredentialByUserId(String userId) {
        return exclusive(() -> readCredentials().stream()
                .filter(value -> userId != null && userId.equalsIgnoreCase(value.getUserId())).findFirst());
    }

    @Override
    public void clearCredentials() {
        exclusive(() -> { publish(null);return null; });
    }

    @Override
    public boolean removeCredential(OAuth2Credential expected) {
        Objects.requireNonNull(expected);
        return exclusive(() -> {
            var current = readCredentials();
            var removed = current.removeIf(value -> value instanceof OAuth2Credential credential
                    && Objects.equals(credential.getIdentityProvider(), expected.getIdentityProvider())
                    && Objects.equals(credential.getAccessToken(), expected.getAccessToken()));
            if (!removed) return false;
            if (current.isEmpty()) {
                publish(null);
            } else writeCredentials(current);
            return true;
        });
    }
}
