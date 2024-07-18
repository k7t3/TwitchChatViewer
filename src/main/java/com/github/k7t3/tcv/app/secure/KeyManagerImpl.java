package com.github.k7t3.tcv.app.secure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;

class KeyManagerImpl implements KeyManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(KeyManagerImpl.class);

    private final Path storePath;

    private final KeyStore store;
    private final KeyStore.PasswordProtection protection;

    private boolean dirty = false; // 永続化されていない鍵情報があるか
    private boolean closed = false; // インスタンスが閉じられているか

    public KeyManagerImpl(Path storePath, char[] password) {
        this.storePath = storePath;
        this.store = loadKeyStore(storePath, password);
        this.protection = new KeyStore.PasswordProtection(password);
    }

    private static KeyStore loadKeyStore(Path storePath, char[] password) {
        if (Files.exists(storePath)) {
            try {
                LOGGER.info("load key store {}", storePath);
                return KeyStore.getInstance(storePath.toFile(), password);
            } catch (KeyStoreException | IOException | CertificateException | NoSuchAlgorithmException e) {
                throw new KeyManagerException(e);
            }
        }

        try {
            var keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, password);
            return keyStore;
        } catch (KeyStoreException | NoSuchAlgorithmException | IOException | CertificateException e) {
            throw new KeyManagerException(e);
        }
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("already closed");
        }
    }

    @Override
    public void flush() {
        LOGGER.info("flushing key store");
        checkClosed();
        try (var output = Files.newOutputStream(storePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            store.store(output, protection.getPassword());
            dirty = false;
        } catch (IOException | NoSuchAlgorithmException | KeyStoreException | CertificateException e) {
            throw new KeyManagerException(e);
        }
    }

    @Override
    public void store(String alias, SecretKey key) {
        LOGGER.info("store {} secret", alias);
        checkClosed();
        var entry = new KeyStore.SecretKeyEntry(key);
        try {
            store.setEntry(alias, entry, protection);
            dirty = true;
        } catch (KeyStoreException e) {
            throw new KeyManagerException(e);
        }
    }

    @Override
    public SecretKey getSecret(String alias) {
        LOGGER.info("get {} secret", alias);
        checkClosed();
        try {
            var entry = store.getEntry(alias, protection);
            if (entry == null)
                return null;
            return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
        } catch (NoSuchAlgorithmException | UnrecoverableEntryException | KeyStoreException e) {
            throw new KeyManagerException(e);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        LOGGER.info("closing key store");
        // 永続化されていない鍵情報がある場合は閉じる前に書き込む
        if (dirty) {
            flush();
        }
        try {
            protection.destroy();
        } catch (DestroyFailedException ignore) {
        }
        closed = true;
    }
}
