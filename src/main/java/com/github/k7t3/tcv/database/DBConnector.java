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

package com.github.k7t3.tcv.database;

import java.io.Closeable;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public interface DBConnector extends Closeable {

    void connect();

    /** @return 操作可能な接続があればtrue。無効化後もcloseによる解放は再試行できる */
    boolean isConnected();

    /** @return 現在のファイル保存先。メモリDBはempty。参照だけではI/Oしない */
    Optional<Path> storagePath();

    void setAutoCommit(boolean autoCommit);

    void commit();

    void rollback();

    /**
     * 自動コミットが無効な接続で更新を実行し、成功時にコミットする。
     * 失敗時はロールバックし、元の例外を再送出する。
     * ロールバックにも失敗した場合は接続を無効化して解放を試み、両方の失敗を元の例外に保持する。
     * 同じ接続の操作は完了まで排他制御される。ネストした呼び出しは拒否する。
     *
     * @param action この接続で同期的に実行する更新。自身でコミットしないこと
     * @throws IllegalStateException 未接続、自動コミットが有効、またはネストした場合
     */
    void transaction(Runnable action);

    /**
     * 結果を複製するまでの読取を一単位で完了し、スナップショットと読取ロックを解放する。
     * 外側のtransaction、または生のSQL APIで開始した手動トランザクション中は
     * その範囲を借用し、呼出元の変更を確定しない。借用時の終了は呼出元が担当する。
     * 自身で読取を開始した場合は、成功・失敗後に元の自動コミット設定を復元する。
     * @param action 同期的な読取。ResultSet等を保持せず、更新は行わないこと
     */
    void readTransaction(Runnable action);

    void execute(String sql);

    void query(String query, SQLFunction<ResultSet> function);

    void prepared(String prepared, SQLFunction<PreparedStatement> function);

    @Override
    void close();
}
