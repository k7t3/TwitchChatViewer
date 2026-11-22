package com.github.k7t3.tcv.support;

import com.github.k7t3.tcv.app.clip.PostedClipItem;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.chat.ClipChatMessage;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.domain.event.chat.ChatRoomEvent;
import com.github.k7t3.tcv.domain.event.chat.ClipPostedEvent;
import java.util.concurrent.Flow;
import static org.mockito.Mockito.*;

/** SDK・ネットワークを開始せず、実際の購読経路からクリップを投稿する。 */
public final class ClipEventSource {
    private final PostedClipRepository repository;
    private final ChatRoom room;
    private Flow.Subscriber<ChatRoomEvent> subscriber;

    public ClipEventSource(PostedClipRepository repository, Twitch twitch, Broadcaster owner) {
        this.repository = repository;
        room = new ChatRoom(twitch, owner, mock(TwitchChannel.class));
        var source = mock(EventSubscribers.class);
        doAnswer(invocation -> {
            subscriber = invocation.getArgument(0);
            subscriber.onSubscribe(mock(Flow.Subscription.class));
            return null;
        }).when(source).subscribeChatEvent(any());
        repository.subscribeEvents(source);
    }

    public PostedClipItem post(String id, VideoClip clip) {
        emit(room, id, clip);
        return repository.getItems().get(id);
    }

    public void emit(ChatRoom source, String id, VideoClip clip) {
        var url = "https://clips.twitch.tv/" + id;
        subscriber.onNext(new ClipPostedEvent(source, ClipChatMessage.of(id, url, url, clip)));
    }

    public ChatRoom room() { return room; }
}
