package com.github.k7t3.tcv.app.model;

import com.github.k7t3.tcv.domain.event.EventSubscribers;

public interface UserViewModel extends AutoCloseable {

    void subscribeEvents(EventSubscribers eventSubscribers);

    /**
     * ログアウトしたときにリソースを開放する
     */
    void onLogout();

    /**
     * アプリケーションの終了時に呼び出す
     */
    void close();

}
