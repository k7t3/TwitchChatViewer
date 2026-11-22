package com.github.k7t3.tcv.domain;

import com.github.k7t3.tcv.domain.channel.FoundChannel;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.exception.InvalidCredentialException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class TwitchEndpointHttpTest {
    private static final String BADGES = """
            {"data":[{"set_id":"subscriber","versions":[{"id":"12","image_url_1x":"https://cdn.example/badge1","image_url_2x":"https://cdn.example/badge2","image_url_4x":"https://cdn.example/badge4","title":"12 months","description":"Subscriber"}]}]}
            """;

    private static final String CLIPS = """
            {"data":[{"id":"clip1","url":"https://clips.twitch.tv/clip1","embed_url":"https://clips.twitch.tv/embed?clip=clip1","broadcaster_id":"42","broadcaster_name":"User 42","creator_id":"99","creator_name":"Clipper","video_id":"","game_id":"10","language":"ja","title":"Clip title","view_count":7,"created_at":"2026-01-01T00:00:00Z","thumbnail_url":"https://cdn.example/clip.jpg","duration":3.5,"vod_offset":null,"is_featured":false}],"pagination":{}}
            """;

    private static final String STREAMS = """
            {"data":[{"id":"stream1","user_id":"42","user_login":"user42","user_name":"User 42","game_id":"10","game_name":"Game","type":"live","title":"Live title","tags":["Test"],"viewer_count":7,"started_at":"2026-01-01T00:00:00Z","language":"ja","thumbnail_url":"https://cdn.example/stream.jpg"},{"id":"stream2","user_id":"43","type":"","started_at":"2026-01-01T00:00:00Z"}],"pagination":{}}
            """;

    private static final String SEARCH = """
            {"data":[
              {"broadcaster_language":"ja","broadcaster_login":"user42","display_name":"User 42","game_id":"10","game_name":"Game","id":"42","is_live":true,"thumbnail_url":"https://cdn.example/42","title":"Live","started_at":"2026-01-01T00:00:00Z"},
              {"broadcaster_language":"en","broadcaster_login":"user43","display_name":"User 43","game_id":"11","game_name":"Other","id":"43","is_live":false,"thumbnail_url":"https://cdn.example/43","title":"Offline","started_at":""}
            ],"pagination":{}}
            """;

    @Test
    void searchOmitsAUserThatDisappearedBeforeTheDetailsLookup() throws Exception {
        try (var server = new FollowedChannelsHttpTest.Endpoint(request -> switch (request.path()) {
            case "/helix/search/channels" -> SEARCH;
            case "/helix/users" -> FollowedChannelsHttpTest.users(List.of("43"));
            default -> throw new AssertionError("unexpected request: " + request.path());
        })) {
            var found = server.api.search("user", false);

            assertThat(found).extracting(FoundChannel::getBroadcaster).doesNotContainNull();
            assertThat(found).extracting(row -> row.getBroadcaster().getUserId()).containsExactly("43");
            assertThat(found.getFirst().isLive()).isFalse();
            assertThat(found.getFirst().getGameName()).isEqualTo("Other");
            assertThat(server.requests).hasSize(2);
            assertThat(server.requests.getLast().query().get("id")).containsExactlyInAnyOrder("42", "43");
        }
    }

    @Test
    void searchReturnsNoEntriesWhenAllDetailsHaveDisappeared() throws Exception {
        try (var server = new FollowedChannelsHttpTest.Endpoint(request ->
                request.path().equals("/helix/search/channels") ? SEARCH : "{\"data\":[]}")) {
            assertThat(server.api.search("user", false)).isEmpty();
            assertThat(server.requests).extracting(FollowedChannelsHttpTest.Request::path)
                    .containsExactly("/helix/search/channels", "/helix/users");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"ja", "en"})
    void searchEnrichesByUserIdAndKeepsThePreferredLanguageFirst(String language) throws Exception {
        var previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag(language));
        try (var server = new FollowedChannelsHttpTest.Endpoint(request ->
                request.path().equals("/helix/search/channels") ? SEARCH
                        : FollowedChannelsHttpTest.users(List.of("43", "42")))) {
            var found = server.api.search("user", false);

            assertThat(found).extracting(row -> row.getBroadcaster().getUserId())
                    .containsExactlyElementsOf(language.equals("ja") ? List.of("42", "43") : List.of("43", "42"));
            assertThat(found).allSatisfy(row -> {
                var first = row.getBroadcaster().getUserId().equals("42");
                assertThat(row.getBroadcaster().getUserLogin()).isEqualTo(first ? "user42" : "user43");
                assertThat(row.isLive()).isEqualTo(first);
                assertThat(row.getGameName()).isEqualTo(first ? "Game" : "Other");
            });
            assertAuthenticated(server);
            assertThat(server.requests.getFirst().query()).containsEntry("query", List.of("user"))
                    .containsEntry("first", List.of("40")).containsEntry("live_only", List.of("false"));
            assertThat(server.requests.getLast().query().get("id")).containsExactlyInAnyOrder("42", "43");
        } finally { Locale.setDefault(previousLocale); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void badgeRequestsMapVersionsAndAllImageSizes(boolean global) throws Exception {
        try (var server = new FollowedChannelsHttpTest.Endpoint(request -> BADGES)) {
            var badges = global ? server.api.getGlobalBadgeSet() : server.api.getChannelBadgeSet(channel());

            assertThat(badges).singleElement().satisfies(set -> {
                assertThat(set.getSetId()).isEqualTo("subscriber");
                assertThat(set.getVersions()).singleElement().satisfies(badge -> {
                    assertThat(badge.getId()).isEqualTo("12");
                    assertThat(badge.getSmallImageUrl()).isEqualTo("https://cdn.example/badge1");
                    assertThat(badge.getMediumImageUrl()).isEqualTo("https://cdn.example/badge2");
                    assertThat(badge.getLargeImageUrl()).isEqualTo("https://cdn.example/badge4");
                    assertThat(badge.getTitle()).isEqualTo("12 months");
                    assertThat(badge.getDescription()).isEqualTo("Subscriber");
                });
            });
            assertThat(server.requests).hasSize(1);
            var request = server.requests.getFirst();
            assertThat(request.path()).isEqualTo(global ? "/helix/chat/badges/global" : "/helix/chat/badges");
            if (global) assertThat(request.query()).isEmpty();
            else assertThat(request.query()).containsExactly(entry("broadcaster_id", List.of("42")));
            assertAuthenticated(server);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aSuccessfulEmptyBadgeResponseRemainsAnEmptyList(boolean global) throws Exception {
        try (var server = new FollowedChannelsHttpTest.Endpoint(request -> "{\"data\":[]}")) {
            var badges = global ? server.api.getGlobalBadgeSet() : server.api.getChannelBadgeSet(channel());

            assertThat(badges).isEmpty();
            assertThat(server.requests).hasSize(1);
        }
    }

    @Test
    void clipsPreserveMetadataAndOnlyReturnIdsPresentInTheResponse() throws Exception {
        try (var server = new FollowedChannelsHttpTest.Endpoint(request -> CLIPS)) {
            var clips = server.api.getClips(List.of("clip1", "missing"));

            assertThat(clips).singleElement().satisfies(clip -> {
                assertThat(clip.getId()).isEqualTo("clip1");
                assertThat(clip.getTitle()).isEqualTo("Clip title");
                assertThat(clip.getBroadcasterId()).isEqualTo("42");
                assertThat(clip.getCreatorId()).isEqualTo("99");
                assertThat(clip.getUrl()).isEqualTo("https://clips.twitch.tv/clip1");
                assertThat(clip.getEmbedUrl()).isEqualTo("https://clips.twitch.tv/embed?clip=clip1");
                assertThat(clip.getThumbnailUrl()).isEqualTo("https://cdn.example/clip.jpg");
                assertThat(clip.getViewCount()).isEqualTo(7);
                assertThat(clip.getDuration()).isEqualTo(3.5f);
                assertThat(clip.getVideoId()).isEmpty();
                assertThat(clip.getCreatedAtInstant()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
            });
            assertThat(server.requests).hasSize(1);
            var request = server.requests.getFirst();
            assertThat(request.path()).isEqualTo("/helix/clips");
            assertThat(request.query()).containsEntry("id", List.of("clip1", "missing"))
                    .doesNotContainKeys("broadcaster_id", "game_id", "after", "before");
            assertAuthenticated(server);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void missingClipIdsReturnAnEmptyListWithinTheSupportedRequestLimit(int count) throws Exception {
        var ids = IntStream.rangeClosed(1, count).mapToObj(i -> "missing" + i).toList();
        try (var server = new FollowedChannelsHttpTest.Endpoint(request -> "{\"data\":[],\"pagination\":{}}")) {
            assertThat(server.api.getClips(ids)).isEmpty();
            assertThat(server.requests).hasSize(1);
            assertThat(server.requests.getFirst().query().get("id")).containsExactlyElementsOf(ids);
            assertAuthenticated(server);
        }
    }

    @Test
    void streamsMapLiveMetadataAndExcludeOfflineAndEmptyTypeRows() throws Exception {
        try (var server = new FollowedChannelsHttpTest.Endpoint(request -> STREAMS)) {
            var streams = server.api.getStreams(List.of("42", "43", "44"));

            assertThat(streams).singleElement().satisfies(stream -> {
                assertThat(stream.userId()).isEqualTo("42");
                assertThat(stream.gameId()).isEqualTo("10");
                assertThat(stream.gameName()).isEqualTo("Game");
                assertThat(stream.title()).isEqualTo("Live title");
                assertThat(stream.tags()).containsExactly("Test");
                assertThat(stream.viewerCount()).isEqualTo(7);
                assertThat(stream.language()).isEqualTo("ja");
                assertThat(stream.startedAt().atZone(ZoneId.systemDefault()).toInstant())
                        .isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
            });
            assertThat(server.requests).hasSize(1);
            assertThat(server.requests.getFirst().path()).isEqualTo("/helix/streams");
            assertThat(server.requests.getFirst().query()).containsEntry("user_id", List.of("42", "43", "44"))
                    .containsEntry("first", List.of("3"));
            assertAuthenticated(server);
        }
    }

    @Test
    void streamLookupsSplitLargeIdListsWithoutDroppingTheFinalBatch() throws Exception {
        var ids = IntStream.rangeClosed(1, 201).mapToObj(Integer::toString).toList();
        try (var server = new FollowedChannelsHttpTest.Endpoint(request -> "{\"data\":[],\"pagination\":{}}")) {
            assertThat(server.api.getStreams(ids)).isEmpty();

            assertThat(server.requests).hasSize(3);
            assertThat(server.requests).extracting(request -> request.query().get("user_id").size()).containsExactly(100, 100, 1);
            assertThat(server.requests.stream().flatMap(request -> request.query().get("user_id").stream()))
                    .containsExactlyElementsOf(ids);
            assertThat(server.requests).allSatisfy(request -> {
                assertThat(request.path()).isEqualTo("/helix/streams");
                assertThat(Integer.parseInt(request.query().get("first").getFirst())).isBetween(1, 100);
            });
            assertAuthenticated(server);
        }
    }

    @Test
    void aMissingSingleStreamIsAnEmptyOptional() throws Exception {
        try (var server = new FollowedChannelsHttpTest.Endpoint(request -> "{\"data\":[],\"pagination\":{}}")) {
            assertThat(server.api.getStream("42")).isEmpty();
            assertThat(server.requests).hasSize(1);
            assertThat(server.requests.getFirst().query().get("user_id")).containsExactly("42");
        }
    }

    @Test
    void emptyUserAndStreamIdListsDoNotFetchUnfilteredData() throws Exception {
        try (var server = new FollowedChannelsHttpTest.Endpoint(request -> { throw new AssertionError("empty lookup sent HTTP"); })) {
            assertThat(server.api.getBroadcasters(List.of())).isEmpty();
            assertThat(server.api.getStreams(List.of())).isEmpty();
            assertThat(server.requests).isEmpty();
        }
    }

    @Test
    void userDetailsMapIdentityAndBothImageFields() throws Exception {
        var response = """
                {"data":[{"id":"43","login":"second","display_name":"二番","profile_image_url":"https://cdn.example/43","offline_image_url":""},
                         {"id":"42","login":"first","display_name":"一番","profile_image_url":"https://cdn.example/42","offline_image_url":"https://cdn.example/offline"}]}
                """;
        try (var server = new FollowedChannelsHttpTest.Endpoint(request -> response)) {
            var users = server.api.getBroadcasters(List.of("42", "43"));

            assertThat(users).extracting(Broadcaster::getUserId, Broadcaster::getUserLogin, Broadcaster::getUserName,
                    Broadcaster::getProfileImageUrl, Broadcaster::getOfflineImageUrl).containsExactlyInAnyOrder(
                    tuple("42", "first", "一番", "https://cdn.example/42", Optional.of("https://cdn.example/offline")),
                    tuple("43", "second", "二番", "https://cdn.example/43", Optional.empty()));
            assertThat(server.requests).hasSize(1);
            assertThat(server.requests.getFirst().path()).isEqualTo("/helix/users");
            assertThat(server.requests.getFirst().query().get("id")).containsExactly("42", "43");
            assertAuthenticated(server);
        }
    }

    @Test
    void aFailedLaterUserBatchDoesNotReturnPartialDataOrRetryAPermanentError() throws Exception {
        var ids = IntStream.rangeClosed(1, 101).mapToObj(Integer::toString).toList();
        try (var server = new FollowedChannelsHttpTest.Endpoint(
                request -> FollowedChannelsHttpTest.users(request.query().get("id")),
                request -> request.query().get("id").contains("101") ? 400 : 200)) {
            assertThatThrownBy(() -> server.api.getBroadcasters(ids)).isInstanceOf(RuntimeException.class)
                    .isNotInstanceOf(InvalidCredentialException.class);

            assertThat(server.requests).hasSize(2);
            assertThat(server.requests).extracting(request -> request.query().get("id").size()).containsExactly(100, 1);
            assertThat(server.api.getBroadcasters(List.of("42"))).extracting(Broadcaster::getUserId).containsExactly("42");
            assertThat(server.requests).hasSize(3);
            assertAuthenticated(server);
        }
    }

    private static Broadcaster channel() { return new Broadcaster("42", "user42", "User 42", "", null); }

    private static void assertAuthenticated(FollowedChannelsHttpTest.Endpoint server) {
        assertThat(server.requests).allSatisfy(request -> {
            assertThat(request.authorization()).isEqualTo("Bearer test-token");
            assertThat(request.clientId()).isEqualTo("test-client");
        });
    }
}
