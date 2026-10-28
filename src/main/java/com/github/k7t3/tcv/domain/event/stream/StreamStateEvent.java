package com.github.k7t3.tcv.domain.event.stream;

import com.github.k7t3.tcv.domain.channel.StreamInfo;

/** 監視で確定した配信状態。SDKのキャッシュとイベント型を購読側へ渡さない。 */
public sealed interface StreamStateEvent {
    String userId();

    record Online(String userId, StreamInfo stream) implements StreamStateEvent {}
    record Offline(String userId) implements StreamStateEvent {}
    record Title(String userId, StreamInfo stream) implements StreamStateEvent {}
    record Game(String userId, StreamInfo stream) implements StreamStateEvent {}
    record Viewers(String userId, StreamInfo stream) implements StreamStateEvent {}
}
