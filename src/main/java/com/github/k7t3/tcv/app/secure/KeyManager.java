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
