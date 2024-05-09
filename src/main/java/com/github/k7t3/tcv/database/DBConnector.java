package com.github.k7t3.tcv.database;

import java.io.Closeable;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public interface DBConnector extends Closeable {

    void connect();

    boolean isConnected();

    void setAutoCommit(boolean autoCommit);

    void commit();

    void rollback();

    void execute(String sql);

    void query(String query, SQLFunction<ResultSet> function);

    void prepared(String prepared, SQLFunction<PreparedStatement> function);

    @Override
    void close();
}
