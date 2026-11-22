package com.github.k7t3.tcv.app.secure;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;

/** 借用した鍵管理サービスを使う。旧 CBC は読取専用とし、新規保存は認証付きの版で行う。 */
final class CredentialCipher {
    private static final byte[] HEADER = {'T', 'C', 'V', 'C', 'R', 'E', 'D', 2};
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String CURRENT_KEY = "credential.gcm.v2";
    private static final String LEGACY_KEY = "c.o";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final KeyManager keys;

    CredentialCipher(KeyManager keys) { this.keys = Objects.requireNonNull(keys); }

    byte[] encrypt(byte[] plain) {
        Objects.requireNonNull(plain);
        var key = keys.getOrCreateSecret(CURRENT_KEY, CredentialCipher::generateKey);
        var nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(HEADER);
            var encrypted = cipher.doFinal(plain);
            var result = Arrays.copyOf(HEADER, HEADER.length + nonce.length + encrypted.length);
            System.arraycopy(nonce, 0, result, HEADER.length, nonce.length);
            System.arraycopy(encrypted, 0, result, HEADER.length + nonce.length, encrypted.length);
            return result;
        } catch (GeneralSecurityException failure) {
            throw new KeyManagerException("failed to encrypt credentials", failure);
        }
    }

    byte[] decrypt(byte[] encrypted) {
        Objects.requireNonNull(encrypted);
        try {
            if (hasMagic(encrypted)) return decryptCurrent(encrypted);
            if (encrypted.length < 32 || encrypted.length % 16 != 0) throw new KeyManagerException("invalid credential format");
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, existingKey(LEGACY_KEY), new IvParameterSpec(encrypted, 0, 16));
            return cipher.doFinal(encrypted, 16, encrypted.length - 16);
        } catch (GeneralSecurityException failure) {
            throw new KeyManagerException("failed to decrypt credentials", failure);
        }
    }

    private byte[] decryptCurrent(byte[] encrypted) throws GeneralSecurityException {
        var bodyOffset = HEADER.length + NONCE_BYTES;
        if (encrypted.length < bodyOffset + TAG_BITS / 8 || encrypted[HEADER.length - 1] != HEADER[HEADER.length - 1]) {
            throw new KeyManagerException("unsupported or truncated credential format");
        }
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        var nonce = Arrays.copyOfRange(encrypted, HEADER.length, bodyOffset);
        cipher.init(Cipher.DECRYPT_MODE, existingKey(CURRENT_KEY), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(HEADER);
        return cipher.doFinal(encrypted, bodyOffset, encrypted.length - bodyOffset);
    }

    private SecretKey existingKey(String alias) {
        var key = keys.getSecret(alias);
        if (key == null) throw new KeyManagerException("credential decryption key is missing");
        return key;
    }

    private static boolean hasMagic(byte[] bytes) {
        var length = HEADER.length - 1;
        return bytes.length >= length && Arrays.equals(HEADER, 0, length, bytes, 0, length);
    }

    private static SecretKey generateKey() {
        try {
            var generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        } catch (GeneralSecurityException failure) {
            throw new KeyManagerException("failed to generate credential key", failure);
        }
    }
}
