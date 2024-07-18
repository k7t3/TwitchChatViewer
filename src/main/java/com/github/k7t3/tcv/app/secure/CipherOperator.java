package com.github.k7t3.tcv.app.secure;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * 暗号化に関するユーティリティクラス
 * <p>
 *     暗号化・復号に使用しているキー情報は{@link KeyManager}で管理されているため、
 *     必要に応じて{@link KeyManagerFactory}より実装を取得し、永続化すること。
 * </p>
 */
public class CipherOperator {

    private static final String KEY_ALIAS = "c.o";

    private static final int IV_LENGTH = 16;
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    private SecretKey secret;

    /**
     * プライベートコンストラクタ
     */
    private CipherOperator() {
    }

    /**
     * キーストアから鍵を取得する
     */
    private SecretKey getSecret() {
        if (secret == null) {
            var manager = KeyManagerFactory.getInstance();
            secret = manager.getSecret(KEY_ALIAS);

            if (secret == null) {
                secret = generateSecretKey();
                manager.store(KEY_ALIAS, secret);
            }
        }
        return secret;
    }

    private static SecretKey generateSecretKey() {
        try {
            var generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 引数のバイト配列を暗号化して返す。
     * <p>
     *     対称鍵暗号方式によって暗号化され、暗号化に使用した鍵は安全なキーストアに保管される。
     * </p>
     * @param bytes 暗号化するバイト配列
     * @return 暗号かれた配列
     * @throws KeyManagerException キーストアに関するエラー
     * @throws NullPointerException 引数がnullのとき
     * @throws IllegalArgumentException 引数が空の時
     */
    public static byte[] encrypt(byte[] bytes) {
        return Instance.OPERATOR.encrypt0(bytes);
    }

    private byte[] encrypt0(byte[] bytes) {
        if (bytes == null)
            throw new NullPointerException("argument is null");
        if (bytes.length < 1)
            throw new IllegalArgumentException("argument is empty");

        Cipher cipher;
        try {
            cipher = Cipher.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }

        var secret = getSecret();
        try {
            cipher.init(Cipher.ENCRYPT_MODE, secret);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        var iv = cipher.getIV();
        assert iv.length == IV_LENGTH;

        byte[] enc;
        try {
            enc = cipher.doFinal(bytes);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }

        // 暗号化配列の先頭に初期化ベクトルを結合して返す
        var result = new byte[enc.length + IV_LENGTH];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(enc, 0, result, IV_LENGTH, enc.length);
        return result;
    }

    /**
     * 暗号化されたバイト配列を復号して返す。
     * <p>
     *     復号するためのキーを安全なキーストアから取得して復号する。
     * </p>
     * @param bytes 復号するバイト配列
     * @return 復号されたバイト配列
     * @throws KeyManagerException キーストアに関するエラー
     * @throws NullPointerException 引数がnullのとき
     * @throws IllegalArgumentException 引数に初期化ベクトルが含まれていないことが疑われるとき
     */
    public static byte[] decrypt(byte[] bytes) {
        return Instance.OPERATOR.decrypt0(bytes);
    }

    private byte[] decrypt0(byte[] bytes) {
        if (bytes == null)
            throw new NullPointerException("argument is null");
        if (bytes.length < IV_LENGTH + 1)
            throw new IllegalArgumentException("argument may not include IV");

        Cipher cipher;
        try {
            cipher = Cipher.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            throw new RuntimeException(e);
        }

        var iv = new byte[IV_LENGTH];
        System.arraycopy(bytes, 0, iv, 0, IV_LENGTH);

        var secret = getSecret();
        var ivSpec = new IvParameterSpec(iv);
        try {
            cipher.init(Cipher.DECRYPT_MODE, secret, ivSpec);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }

        byte[] result;
        try {
            result = cipher.doFinal(bytes, IV_LENGTH, bytes.length - IV_LENGTH);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    private static final class Instance {
        private static final CipherOperator OPERATOR = new CipherOperator();
    }

}
