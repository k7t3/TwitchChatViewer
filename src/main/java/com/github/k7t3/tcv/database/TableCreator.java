package com.github.k7t3.tcv.database;

public interface TableCreator {

    void create(DBConnector connector);

    TableCreator DEFAULT = connector -> {
        connector.execute("""
                create table if not exists groups (
                    id text primary key,
                    name text not null,
                    created_at datetime not null,
                    updated_at datetime not null
                );
                """);
        connector.execute("""
                create index groups_updated_at on groups (updated_at desc);
                """);
        connector.execute("""
                create table if not exists group_users (
                    group_id text not null,
                    user_id text not null,
                    primary key (group_id, user_id),
                    constraint fk_group_users_group_id foreign key (group_id) references groups(id) on delete cascade
                );
                """);
    };

}
