package com.github.k7t3.tcv.database;

import java.sql.SQLException;
import java.util.function.Consumer;

@FunctionalInterface
public interface SQLFunction<T> {

    void invoke(T t) throws SQLException;

}
