package com.github.k7t3.tcv.database;

import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLiteDBConnector extends AbstractDBConnector {

    private static final String DRIVER = "org.sqlite.JDBC";
    private static final String HEADER = "jdbc:sqlite:";

    static {
        try {
            Class.forName(DRIVER);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Path filePath;

    private final String connectionURL;

    private final TableCreator tableCreator;

    /**
     * テスト用のインメモリモードコンストラクタ
     */
    SQLiteDBConnector(TableCreator creator) {
        this.filePath = null;
        this.connectionURL = HEADER + ":memory:";
        this.tableCreator = creator;
    }

    public SQLiteDBConnector(Path filePath, TableCreator creator) {
        this.filePath = filePath;
        this.connectionURL = HEADER + filePath.toAbsolutePath();
        this.tableCreator = creator;
    }

    private void createTable() {
        this.tableCreator.create(this);
    }

    @Override
    protected Connection connectImpl() {
        try {
            var config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            config.setJournalMode(SQLiteConfig.JournalMode.MEMORY);
            config.setSynchronous(SQLiteConfig.SynchronousMode.OFF);
            config.setDatePrecision(SQLiteConfig.DatePrecision.MILLISECONDS.getValue());

            return DriverManager.getConnection(connectionURL, config.toProperties());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void connect() {
        // メモリモードのときか、対象のファイルが存在しないとき
        boolean empty = (filePath == null || !Files.exists(filePath));

        super.connect();

        if (empty) {
            createTable();
        }

        // デフォルトでAutoCommitはFalse
        setAutoCommit(false);
    }

    /**
     * 接続するファイルを変更する
     * <p>
     *     接続済みの場合は切断後、新しいファイルパスに対して接続を試行する。
     * </p>
     * <p>
     *     SQLiteの使用中に移動することを考えたくないので自由に切断できるタイミングで実行すること。
     * </p>
     * @param filePath 接続先を切り替えるファイル
     */
    public void updateFilePath(Path filePath) {
        var connected = isConnected();
        if (connected) {
            close();
        }
        this.filePath = filePath;
        if (connected) {
            connect();
        }
    }

}
