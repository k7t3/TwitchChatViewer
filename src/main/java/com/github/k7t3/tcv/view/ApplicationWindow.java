package com.github.k7t3.tcv.view;

/** 生成直後から所有し、画面構築の途中失敗でも解放できるウインドウ。 */
interface ApplicationWindow extends AutoCloseable {
    void show();
    @Override void close();
}
