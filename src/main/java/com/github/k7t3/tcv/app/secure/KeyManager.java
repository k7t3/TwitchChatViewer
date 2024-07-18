package com.github.k7t3.tcv.app.secure;

import javax.crypto.SecretKey;

/**
 * 鍵を安全に永続化するためのクラス
 */
public interface KeyManager extends AutoCloseable {

    /**
     * バッファリングされる鍵情報を永続化する
     * @throws KeyManagerException キーストアに関するエラー
     */
    void flush() throws KeyManagerException;

    /**
     * 任意のエイリアスに紐づく鍵を保存する
     * @param alias 任意のエイリアス
     * @param key 保存する鍵
     * @throws KeyManagerException キーストアに関するエラー
     */
    void store(String alias, SecretKey key) throws KeyManagerException;

    /**
     * エイリアスに紐づいたキーストアに保存される鍵を取得する
     * @param alias 紐づけられるエイリアス
     * @return エイリアスに紐づく鍵か、該当するものがないときはnull
     * @throws KeyManagerException キーストアに関するエラー
     */
    SecretKey getSecret(String alias) throws KeyManagerException;

    @Override
    void close();
}
