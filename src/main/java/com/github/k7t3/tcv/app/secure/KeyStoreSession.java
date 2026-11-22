package com.github.k7t3.tcv.app.secure;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.security.auth.DestroyFailedException;

/** ファイルの排他区間だけ復号用パスワードを所有し、区間終了時に消去する。 */
final class KeyStoreSession implements AutoCloseable {
    private static final byte[] MAGIC = {'T', 'C', 'V', 'K', 'E', 'Y', 'S', 2};
    private static final int HEADER_SIZE = MAGIC.length + 2 * Long.BYTES;
    private final KeyStore store;
    private final char[] password;
    private final OsSecretStore secrets;
    private final UUID id;
    private final byte[] secret;
    private boolean newSecret;

    private KeyStoreSession(KeyStore store, char[] password, OsSecretStore secrets, UUID id,
                            byte[] secret, boolean newSecret) {
        this.store = store;
        this.password = password;
        this.secrets = secrets;
        this.id = id;
        this.secret = secret;
        this.newSecret = newSecret;
    }

    static KeyStoreSession passwordProtected(Path path, char[] password) throws IOException, GeneralSecurityException {
        var owned = password.clone();
        try {
            var store = Files.exists(path) ? KeyStore.getInstance(path.toFile(), owned) : empty(KeyStore.getDefaultType(), owned);
            return new KeyStoreSession(store, owned, null, null, null, false);
        } catch (IOException | GeneralSecurityException | RuntimeException | Error failure) {
            Arrays.fill(owned, '\0');
            throw failure;
        }
    }

    static KeyStoreSession osProtected(Path path, OsSecretStore secrets, AtomicKeyStoreFile files)
            throws IOException, GeneralSecurityException, DestroyFailedException {
        var data = Files.exists(path) ? Files.readAllBytes(path) : null;
        KeyStore legacy = null;
        var protectedFormat = data != null && data.length >= MAGIC.length - 1
                && Arrays.equals(data, 0, MAGIC.length - 1, MAGIC, 0, MAGIC.length - 1);
        if (data != null && !protectedFormat) {
            var legacyPassword = "changeit".toCharArray();
            try { legacy = KeyStore.getInstance(path.toFile(), legacyPassword); }
            finally { Arrays.fill(legacyPassword, '\0'); }
        }
        var creating = data == null || legacy != null;
        UUID id;
        byte[] secret;
        if (creating) {
            id = UUID.randomUUID();
            secret = new byte[32];
            new SecureRandom().nextBytes(secret);
        } else {
            if (data.length < HEADER_SIZE || !Arrays.equals(data, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) {
                throw new KeyManagerException("unsupported protected key-store format");
            }
            var header = ByteBuffer.wrap(data);
            header.position(MAGIC.length);
            id = new UUID(header.getLong(), header.getLong());
            secret = secrets.read(id.toString());
            if (secret == null) throw new KeyManagerException("OS key-store secret is missing");
        }
        char[] password = null;
        try {
            if (secret.length != 32) throw new KeyManagerException("invalid OS key-store secret");
            password = password(secret);
            var store = empty("PKCS12", password);
            if (legacy != null) {
                copyEntries(legacy, store, password);
            } else if (data != null) {
                try (var input = new ByteArrayInputStream(data, HEADER_SIZE, data.length - HEADER_SIZE)) {
                    store.load(input, password);
                }
            }
            var session = new KeyStoreSession(store, password, secrets, id, secret, creating);
            if (legacy != null) session.persist(path, files);
            return session;
        } catch (IOException | GeneralSecurityException | DestroyFailedException | RuntimeException | Error failure) {
            Arrays.fill(secret, (byte) 0);
            if (password != null) Arrays.fill(password, '\0');
            throw failure;
        }
    }

    private static void copyEntries(KeyStore source, KeyStore target, char[] password)
            throws GeneralSecurityException, DestroyFailedException {
        // 固定パスワードは旧形式の読取だけに使用し、新形式へは保存しない。
        var legacyPassword = "changeit".toCharArray();
        var from = new KeyStore.PasswordProtection(legacyPassword);
        Arrays.fill(legacyPassword, '\0');
        var to = new KeyStore.PasswordProtection(password);
        try {
            var aliases = source.aliases();
            while (aliases.hasMoreElements()) {
                var alias = aliases.nextElement();
                var entry = source.getEntry(alias, source.isKeyEntry(alias) ? from : null);
                target.setEntry(alias, entry, entry instanceof KeyStore.TrustedCertificateEntry ? null : to);
            }
        } finally {
            try { from.destroy(); }
            finally { to.destroy(); }
        }
    }

    private static KeyStore empty(String type, char[] password) throws GeneralSecurityException, IOException {
        var store = KeyStore.getInstance(type);
        store.load(null, password);
        return store;
    }

    private static char[] password(byte[] secret) {
        var result = new char[secret.length * 2];
        var alphabet = "0123456789abcdef";
        for (var i = 0; i < secret.length; i++) {
            result[i * 2] = alphabet.charAt((secret[i] & 0xff) >>> 4);
            result[i * 2 + 1] = alphabet.charAt(secret[i] & 0xf);
        }
        return result;
    }

    SecretKey getSecret(String alias) throws GeneralSecurityException, DestroyFailedException {
        var protection = new KeyStore.PasswordProtection(password);
        try {
            var entry = store.getEntry(alias, protection);
            if (entry == null) return null;
            if (!(entry instanceof KeyStore.SecretKeyEntry key)) throw new KeyManagerException("entry is not a secret key");
            return key.getSecretKey();
        } finally { protection.destroy(); }
    }

    void put(String alias, SecretKey key) throws GeneralSecurityException, DestroyFailedException {
        var protection = new KeyStore.PasswordProtection(password);
        try { store.setEntry(alias, new KeyStore.SecretKeyEntry(key), protection); }
        finally { protection.destroy(); }
    }

    void persist(Path path, AtomicKeyStoreFile files) throws GeneralSecurityException, IOException {
        try (var output = new ByteArrayOutputStream()) {
            store.store(output, password);
            if (secrets == null) {
                files.write(path, output.toByteArray());
            } else {
                var payload = output.toByteArray();
                var envelope = ByteBuffer.allocate(HEADER_SIZE + payload.length).put(MAGIC)
                        .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).put(payload).array();
                // OS側の保存が終わる前に、その秘密を参照するファイルを公開しない。
                if (newSecret) {
                    secrets.save(id.toString(), secret);
                    newSecret = false;
                }
                files.write(path, envelope);
            }
        }
    }

    @Override public void close() {
        Arrays.fill(password, '\0');
        if (secret != null) Arrays.fill(secret, (byte) 0);
    }
}
