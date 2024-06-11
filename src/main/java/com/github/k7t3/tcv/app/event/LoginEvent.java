package com.github.k7t3.tcv.app.event;

import com.github.k7t3.tcv.domain.Twitch;

/**
 * デバイス認証フローが正常に終了したときか
 * 発行済のトークンを使って認証できたときに発行されるイベント
 * <p>
 *     このイベントをトリガーに{@link ClientLoadedEvent}が発行される
 * </p>
 */
public class LoginEvent implements AppEvent {
}
