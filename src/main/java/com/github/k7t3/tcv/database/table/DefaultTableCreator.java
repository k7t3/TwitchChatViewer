package com.github.k7t3.tcv.database.table;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.DatabaseVersion;

public class DefaultTableCreator extends AbstractTableCreator {

    public DefaultTableCreator(DatabaseVersion version) {
        super(DatabaseVersion.EMPTY, version);
    }

    @Override
    protected void modifyTables(DBConnector connector) {
        connector.execute("""
                create table if not exists groups (
                    id text primary key,
                    name text not null,
                    comment text,
                    pinned boolean not null,
                    created_at datetime not null,
                    updated_at datetime not null
                );
                """);
        connector.execute("""
                create index groups_order on groups (pinned desc, updated_at desc);
                """);
        connector.execute("""
                create table if not exists group_users (
                    group_id text not null,
                    user_id text not null,
                    primary key (group_id, user_id),
                    constraint fk_group_users_group_id foreign key (group_id) references groups(id) on delete cascade
                );
                """);
        connector.execute("""
                create table if not exists windows (
                    id text primary key,
                    x double not null,
                    y double not null,
                    width double not null,
                    height double not null,
                    maximized boolean not null
                );
                """);
        connector.execute("""
                create table chat_keyword_filter (
                    id integer primary key autoincrement,
                    type integer not null,
                    keyword text not null
                );
                """);
        connector.execute("""
                create table chat_user_filter (
                    user_id text primary key,
                    user_name text not null,
                    comment text not null
                );
                """);
    }

}
