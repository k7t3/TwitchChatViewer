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

package com.github.k7t3.tcv.app.secure;

import com.github.k7t3.tcv.io.InterruptibleFileLocks;

import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantLock;

/** キャッシュした古い KeyStore を書き戻さず、ファイルの読取・変更・置換を一組で行う。 */
class KeyManagerImpl implements KeyManager {
    // FileLock は同じ JVM 内の重複取得を待機しないため、まず JVM 内を直列化する。
    private static final ReentrantLock FILE_LOCK = new ReentrantLock();
    private final ReentrantLock lifecycleLock = new ReentrantLock();
    private final Path storePath;
    private final char[] password;
    private final OsSecretStore secrets;
    private final AtomicKeyStoreFile files;
    private boolean closed;

    public KeyManagerImpl(Path path, char[] password) { this(path, password, new AtomicKeyStoreFile()); }

    KeyManagerImpl(Path path, OsSecretStore secrets) {
        this(path, null, Objects.requireNonNull(secrets), new AtomicKeyStoreFile());
    }

    KeyManagerImpl(Path path, char[] password, AtomicKeyStoreFile files) {
        this(path, Objects.requireNonNull(password), null, files);
    }

    private KeyManagerImpl(Path path, char[] password, OsSecretStore secrets, AtomicKeyStoreFile files) {
        this.storePath = Objects.requireNonNull(path).toAbsolutePath().normalize();
        this.password = password == null ? null : password.clone();
        this.secrets = secrets;
        this.files = Objects.requireNonNull(files);
        try { withStore(store -> null); }
        catch (RuntimeException | Error failure) {
            if (this.password != null) Arrays.fill(this.password, '\0');
            throw failure;
        }
    }

    @Override
    public SecretKey getOrCreateSecret(String alias, Supplier<SecretKey> generator) {
        Objects.requireNonNull(generator);
        return withStore(store -> {
            var existing = store.getSecret(Objects.requireNonNull(alias));
            if (existing != null) return existing;
            var created = Objects.requireNonNull(generator.get());
            store.put(alias, created);
            store.persist(storePath, files);
            return created;
        });
    }

    @Override
    public void store(String alias, SecretKey key) {
        withStore(store -> {
            store.put(Objects.requireNonNull(alias), Objects.requireNonNull(key));
            store.persist(storePath, files);
            return null;
        });
    }

    @Override
    public SecretKey getSecret(String alias) { return withStore(store -> store.getSecret(Objects.requireNonNull(alias))); }

    @FunctionalInterface
    private interface Operation<T> { T apply(KeyStoreSession store) throws IOException, GeneralSecurityException, DestroyFailedException; }

    private <T> T withStore(Operation<T> operation) {
        try {
            lifecycleLock.lockInterruptibly();
            try {
                if (closed) throw new IllegalStateException("key manager is closed");
                FILE_LOCK.lockInterruptibly();
                try {
                    var parent = storePath.getParent();
                    Files.createDirectories(parent, AtomicKeyStoreFile.permissions(parent, "rwx------"));
                    var lockPath = storePath.resolveSibling(storePath.getFileName() + ".lock");
                    try (var channel = FileChannel.open(lockPath,
                            Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE),
                            AtomicKeyStoreFile.permissions(lockPath, "rw-------")); var ignored = InterruptibleFileLocks.acquire(channel)) {
                        try (var store = secrets == null ? KeyStoreSession.passwordProtected(storePath, password)
                                : KeyStoreSession.osProtected(storePath, secrets, files)) {
                            return operation.apply(store);
                        }
                    }
                } finally { FILE_LOCK.unlock(); }
            } finally { lifecycleLock.unlock(); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            var failure = new InterruptedIOException("interrupted while waiting for key storage");
            failure.initCause(interrupted);
            throw new KeyManagerException("failed to access key store", failure);
        } catch (IOException | GeneralSecurityException | DestroyFailedException failure) {
            throw new KeyManagerException("failed to access key store", failure);
        }
    }

    @Override
    public void close() {
        // closeは受理済みの保存を中断せず、その実完了と秘密消去を待つ。
        lifecycleLock.lock();
        try {
            if (closed) return;
            closed = true;
            if (password != null) Arrays.fill(password, '\0');
        } finally { lifecycleLock.unlock(); }
    }
}
