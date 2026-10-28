package com.github.k7t3.tcv.domain;

import com.github.twitch4j.helix.domain.ChannelSearchList;
import com.github.twitch4j.helix.domain.ChatBadgeSetList;
import com.github.twitch4j.helix.domain.ClipList;
import com.github.twitch4j.helix.domain.OutboundFollowing;
import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.UserList;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import java.time.Instant;
import java.util.List;

/** アプリが使用するHelix HTTP契約。実行・再試行・終了の所有はSDKのコマンド型に依存しない。 */
public interface HelixClient {
    @RequestLine("GET /channels/followed?user_id={user_id}&broadcaster_id={broadcaster_id}&first={first}&after={after}")
    @Headers("Authorization: Bearer {token}")
    OutboundFollowing getFollowedChannels(@Param("token") String token, @Param("user_id") String userId,
            @Param("broadcaster_id") String broadcasterId, @Param("first") Integer first, @Param("after") String after);

    @RequestLine("GET /search/channels?after={after}&first={first}&live_only={live_only}&query={query}")
    @Headers("Authorization: Bearer {token}")
    ChannelSearchList searchChannels(@Param("token") String token, @Param("query") String query,
            @Param("first") Integer first, @Param("after") String after, @Param("live_only") Boolean liveOnly);

    @RequestLine("GET /users?id={id}&login={login}")
    @Headers("Authorization: Bearer {token}")
    UserList getUsers(@Param("token") String token, @Param("id") List<String> ids, @Param("login") List<String> logins);

    @RequestLine("GET /streams?after={after}&before={before}&first={first}&game_id={game_id}&language={language}&user_id={user_id}&user_login={user_login}")
    @Headers("Authorization: Bearer {token}")
    StreamList getStreams(@Param("token") String token, @Param("after") String after, @Param("before") String before,
            @Param("first") Integer first, @Param("game_id") List<String> gameIds, @Param("language") List<String> languages,
            @Param("user_id") List<String> userIds, @Param("user_login") List<String> userLogins);

    @RequestLine("GET /chat/badges/global")
    @Headers("Authorization: Bearer {token}")
    ChatBadgeSetList getGlobalChatBadges(@Param("token") String token);

    @RequestLine("GET /chat/badges?broadcaster_id={broadcaster_id}")
    @Headers("Authorization: Bearer {token}")
    ChatBadgeSetList getChannelChatBadges(@Param("token") String token, @Param("broadcaster_id") String broadcasterId);

    @RequestLine("GET /clips?broadcaster_id={broadcaster_id}&game_id={game_id}&id={id}&after={after}&before={before}&first={first}&started_at={started_at}&ended_at={ended_at}&is_featured={is_featured}")
    @Headers("Authorization: Bearer {token}")
    ClipList getClips(@Param("token") String token, @Param("broadcaster_id") String broadcasterId,
            @Param("game_id") String gameId, @Param("id") List<String> ids, @Param("after") String after,
            @Param("before") String before, @Param("first") Integer first, @Param("started_at") Instant startedAt,
            @Param("ended_at") Instant endedAt, @Param("is_featured") Boolean featured);
}
