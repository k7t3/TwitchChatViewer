package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.DBConnector;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** 設定投影の復旧情報をフィルターと同じDBに保存する。接続は借用する。 */
public final class SettingsJournal {
    private static final Object JVM_GUARD = new Object();

    /** 編集前後の設定とSQLの確定状態。旧記録はpreviousOwnersが空。配列は呼出元と共有しない。 */
    public record Entry(UUID operation, String nodePath, boolean userNode, byte[] before, byte[] after,
                        boolean committed, Optional<byte[]> previousOwners) {
        public Entry {
            Objects.requireNonNull(operation);
            Objects.requireNonNull(nodePath);
            before = before.clone();
            after = after.clone();
            previousOwners = previousOwners.map(bytes -> bytes.clone());
        }
        /** 旧記録には更新前の所有情報がなく、従来どおりの復旧を行う。 */
        public Entry(UUID operation, String nodePath, boolean userNode, byte[] before, byte[] after, boolean committed) {
            this(operation, nodePath, userNode, before, after, committed, Optional.empty());
        }
        @Override public byte[] before() { return before.clone(); }
        @Override public byte[] after() { return after.clone(); }
        @Override public Optional<byte[]> previousOwners() { return previousOwners.map(bytes -> bytes.clone()); }
    }

    private final DBConnector connector;

    SettingsJournal(DBConnector connector) { this.connector = Objects.requireNonNull(connector); }

    /**
     * 保存・復旧を排他し、補助テーブルを準備して同期的に実行する。
     * ファイルDBはrealPathに対応するsidecar FileLockを完了まで保持する。
     * コンストラクターではI/Oしない。メモリDBはJVM内だけで排他する。
     * @param operation この接続で実行する保存または復旧
     * @return operationの結果
     */
    public <T> T exclusive(Supplier<T> operation) {
        // 接続monitorより先に取得し、同一JVMのFileLock重複とlock用mapの増加を避ける。
        synchronized (JVM_GUARD) {
            synchronized (connector) {
                var path = connector.storagePath();
                if (path.isEmpty()) return initializeAndRun(operation);
                try {
                    var real = path.orElseThrow().toRealPath();
                    var lockPath = real.resolveSibling(real.getFileName() + ".settings.lock");
                    try (var channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                         var lock = channel.lock()) {
                        return initializeAndRun(operation);
                    }
                } catch (IOException failure) { throw new UncheckedIOException(failure); }
            }
        }
    }

    private <T> T initializeAndRun(Supplier<T> operation) {
        connector.transaction(() -> {
            connector.execute("""
                create table if not exists settings_recovery (
                  slot integer primary key check (slot = 1),
                  operation_id text not null,
                  node_path text not null,
                  user_node integer not null check (user_node in (0, 1)),
                  before_values blob not null,
                  after_values blob not null,
                  committed integer not null check (committed in (0, 1))
                );
                """);
            connector.execute("""
                create table if not exists settings_recovery_owners (
                  operation_id text primary key,
                  before_owners blob not null
                );
                """);
        });
        return operation.get();
    }

    /** @return 現在の復旧情報。exclusiveの内側で呼び出すこと */
    public Optional<Entry> pending() {
        var entry = new Entry[1];
        connector.readTransaction(() -> connector.query("""
                select recovery.*, owners.before_owners
                from settings_recovery recovery
                left join settings_recovery_owners owners on recovery.operation_id = owners.operation_id
                where recovery.slot = 1;
                """, rows -> {
            if (rows.next()) entry[0] = new Entry(UUID.fromString(rows.getString("operation_id")),
                    rows.getString("node_path"), rows.getBoolean("user_node"), rows.getBytes("before_values"),
                    rows.getBytes("after_values"), rows.getBoolean("committed"),
                    Optional.ofNullable(rows.getBytes("before_owners")));
        }));
        return Optional.ofNullable(entry[0]);
    }

    /**
     * 未確定の復旧情報を独立してcommitする。既存の情報を上書きしない。
     * @param entry committed=falseの新しい操作。exclusiveの内側で渡すこと
     */
    public void prepare(Entry entry) {
        if (entry.committed()) throw new IllegalArgumentException("recovery entry is already committed");
        connector.transaction(() -> {
            connector.prepared("""
                insert into settings_recovery
                (slot, operation_id, node_path, user_node, before_values, after_values, committed)
                values (1, ?, ?, ?, ?, ?, 0);
                """, statement -> {
                statement.setString(1, entry.operation().toString());
                statement.setString(2, entry.nodePath());
                statement.setBoolean(3, entry.userNode());
                statement.setBytes(4, entry.before());
                statement.setBytes(5, entry.after());
                statement.executeUpdate();
            });
            entry.previousOwners().ifPresent(owners -> connector.prepared("""
                    insert into settings_recovery_owners (operation_id, before_owners) values (?, ?);
                    """, statement -> {
                statement.setString(1, entry.operation().toString());
                statement.setBytes(2, owners);
                statement.executeUpdate();
            }));
        });
    }

    /**
     * フィルター更新と同じtransactionで確定予定を記す。自身ではcommitしない。
     * @param operation prepareした操作の識別子
     */
    public void markCommitted(UUID operation) {
        connector.prepared("update settings_recovery set committed = 1 where slot = 1 and operation_id = ?;", statement -> {
            statement.setString(1, operation.toString());
            if (statement.executeUpdate() != 1) throw new IllegalStateException("settings recovery entry is missing");
        });
    }

    /**
     * 投影または補償が完了した操作の復旧情報を消す。別操作の情報は消さない。
     * @param operation 終了した操作。exclusiveの内側で渡すこと
     */
    public void clear(UUID operation) {
        connector.transaction(() -> {
            connector.prepared(
                "delete from settings_recovery where slot = 1 and operation_id = ?;", statement -> {
                    statement.setString(1, operation.toString());
                    statement.executeUpdate();
                });
            connector.prepared("delete from settings_recovery_owners where operation_id = ?;", statement -> {
                statement.setString(1, operation.toString());
                statement.executeUpdate();
            });
        });
    }
}
