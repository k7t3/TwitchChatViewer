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
import java.util.function.Supplier;

/**
 * 鍵を安全に永続化するためのクラス
 */
public interface KeyManager extends AutoCloseable {

    /** 存在する鍵を返す。新規の場合は永続化に成功してから返す。生成と保存は排他的に行う。 */
    SecretKey getOrCreateSecret(String alias, Supplier<SecretKey> generator) throws KeyManagerException;

    /**
     * 任意のエイリアスに紐づく鍵を永続化し、完了後に戻る。
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
