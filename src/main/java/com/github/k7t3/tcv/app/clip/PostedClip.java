package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import javafx.beans.property.ReadOnlyIntegerProperty;

import java.util.Set;

/**
 * チャットに投稿されたクリップ
 */
public interface PostedClip {

    /**
     * 引数の配信にてクリップが投稿された
     * @param broadcaster 配信
     */
    void onPosted(Broadcaster broadcaster);

    /**
     * このクリップが投稿された配信を返す
     * @return 投稿された配信
     */
    Set<Broadcaster> getPostedChannels();

    /**
     * 特定の配信にて投稿されているか
     * @param broadcaster 配信
     * @return 特定の配信にて投稿されているか
     */
    boolean isPosted(Broadcaster broadcaster);

    /**
     * 投稿されたクリップの一覧からこのクリップを削除する
     */
    void remove();

    /**
     * クリップのリンクをブラウザ(JVMの実装に基づく)で開く
     *
     * @return バックグラウンドでクリップを開くタスク
     */
    FXTask<?> browseClipPage();

    /**
     * クリップのリンクをクリップボードにコピーする
     */
    void copyClipURL();

    /**
     * このクリップが投稿された回数を表すプロパティを返す
     * @return このクリップが投稿された回数を表すプロパティ
     */
    ReadOnlyIntegerProperty timesProperty();

    /**
     * このクリップが投稿された回数を返す
     * <p>
     *     開いているすべての配信で投稿された回数
     * </p>
     * @return このクリップが投稿された回数
     */
    int getTimes();

}
