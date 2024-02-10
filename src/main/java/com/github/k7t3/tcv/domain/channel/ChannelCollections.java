package com.github.k7t3.tcv.domain.channel;

import com.github.k7t3.tcv.domain.Twitch;
import com.github.twitch4j.helix.domain.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Twitchのチャンネルに関する機能を提供するクラス
 */
public class ChannelCollections {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchChannel.class);

    private static final int PAGINATION_LIMIT = 100;

    private final Twitch twitch;

    public ChannelCollections(Twitch twitch) {
        this.twitch = twitch;
    }

    public List<Broadcaster> getFollowedBroadcasters() {
        // すべてのフォローしているチャンネルを取得
        var broadcasters = new ArrayList<Broadcaster>();
        getFollowedBroadcasters(null, broadcasters);

        LOGGER.info("got broadcasters {}", broadcasters.size());

        // チャンネルオーナーのプロファイルイメージを取得
        getAllBroadcasterProfileImage(broadcasters);

        return broadcasters;
    }

    private void getFollowedBroadcasters(String cursor, List<Broadcaster> broadcasters) {
        var client = twitch.getClient();
        var helix = client.getHelix();

        var command = helix.getFollowedChannels(twitch.getAccessToken(), twitch.getUserId(), null, PAGINATION_LIMIT, cursor);
        var result = command.execute();

        var follows = result.getFollows();
        if (follows == null) return;

        for (var follow : follows) {
            broadcasters.add(new Broadcaster(follow.getBroadcasterId(), follow.getBroadcasterLogin(), follow.getBroadcasterName()));
        }

        if (result.getPagination().getCursor() != null) {
            getFollowedBroadcasters(result.getPagination().getCursor(), broadcasters);
        }
    }

    /**
     * チャンネルのブロードキャスター情報にはユーザープロファイルが含まれていないため、別途取得する必要がある。
     * @param broadcasters プロファイルを取得するブロードキャスター
     */
    private void getAllBroadcasterProfileImage(List<Broadcaster> broadcasters) {
        int begin = 0;
        int end = Math.min(PAGINATION_LIMIT, broadcasters.size());

        do {

            var split = broadcasters.subList(begin, end);
            getBroadCasterProfileImage(split);

            end = Math.min(end + PAGINATION_LIMIT, broadcasters.size());
        } while (end != broadcasters.size());
    }

    private void getBroadCasterProfileImage(List<Broadcaster> broadcasters) {
        if (broadcasters == null || broadcasters.isEmpty()) {
            return;
        }

        var client = twitch.getClient();
        var helix = client.getHelix();
        var userIds = broadcasters.stream().map(Broadcaster::getUserId).toList();

        var command = helix.getUsers(twitch.getAccessToken(), userIds, null);
        var result = command.execute();

        var users = result.getUsers();
        for (var user : users) {

            for (var broadcaster : broadcasters) {

                if (broadcaster.getUserId().equalsIgnoreCase(user.getId())) {
                    broadcaster.setProfileImageUrl(user.getProfileImageUrl());
                    break;
                }
            }
        }
    }

    public List<Stream> getFollowedLiveStreams() {
        var list = new ArrayList<Stream>();
        getFollowedLiveStreams(null, list);

        LOGGER.info("got live streams {}", list.size());

        return list;
    }

    private void getFollowedLiveStreams(String cursor, List<Stream> streams) {
        var client = twitch.getClient();
        var helix = client.getHelix();

        var command = helix.getFollowedStreams(twitch.getAccessToken(), twitch.getUserId(), cursor, null);
        var result = command.execute();

        streams.addAll(result.getStreams());

        if (result.getPagination().getCursor() != null) {
            getFollowedLiveStreams(result.getPagination().getCursor(), streams);
        }
    }

    /**
     * チャンネルを検索するメソッド。キーワードは先頭一致。
     * @param startsWith 先頭一致の検索キーワード
     * @param liveOnly ライブ中のもののみか
     * @return チャンネルの検索結果
     */
    public List<FoundChannel> search(String startsWith, boolean liveOnly) {
        var client = twitch.getClient();
        var helix = client.getHelix();

        var locale = Locale.getDefault();
        var lang = locale.getLanguage();

        var command = helix.searchChannels(twitch.getAccessToken(), startsWith, 30, null, liveOnly);
        var result = command.execute();
        var channels = result.getResults().stream()
                .sorted((r1, r2) -> {
                    // システムの言語と同じ言語を優先するように並べ替える
                    var l1 = r1.getBroadcasterLanguage();
                    var l2 = r2.getBroadcasterLanguage();

                    if (l1.equalsIgnoreCase(l2)) {
                        return l1.equalsIgnoreCase(lang) ? 0 : l1.compareTo(l2);
                    }
                    if (l1.equalsIgnoreCase(lang)) {
                        return -1;
                    }
                    if (l2.equalsIgnoreCase(lang)) {
                        return 1;
                    }
                    return l1.compareTo(l2);

                })
                .map(r -> new FoundChannel(new Broadcaster(r.getId(), r.getBroadcasterLogin(), r.getDisplayName()), r.getIsLive(), r.getGameName()))
                .toList();

        var broadcasters = channels.stream().map(FoundChannel::getBroadcaster).toList();
        getAllBroadcasterProfileImage(broadcasters);

        return channels;
    }

    /**
     * 引数のチャンネルのうち、ライブ配信中の情報を取得するメソッド。
     * @param userIds ブロードキャスターID
     * @return ライブ配信情報
     */
    public List<Stream> getLiveStreams(List<String> userIds) {
        var client = twitch.getClient();
        var helix = client.getHelix();

        var limit = Math.clamp(userIds.size(), 1, 100);

        var command = helix.getStreams(twitch.getAccessToken(), null, null, limit, null, null, userIds, null);
        var result = command.execute();
        var streams = result.getStreams();

        if (streams.isEmpty()) {
            return List.of();
        }

        return List.copyOf(streams);
    }

    /**
     * ユーザーIDを指定してそのチャンネルのライブ情報を取得するメソッド。
     * <p>
     *     値が返ってこないときはライブ配信していない。
     * </p>
     * @param userId ブロードキャスターID
     * @return ユーザーIDに基づくライブ情報
     */
    public Optional<Stream> getLiveStream(String userId) {
        return getLiveStreams(List.of(userId)).stream().findFirst();
    }

}
