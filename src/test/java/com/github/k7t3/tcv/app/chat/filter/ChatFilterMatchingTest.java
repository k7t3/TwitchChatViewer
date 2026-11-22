package com.github.k7t3.tcv.app.chat.filter;

import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.domain.chat.ChatMessageFragment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ChatFilterMatchingTest {
    @ParameterizedTest(name = "keyword matching mode: {0}")
    @CsvSource({
            "CONTAINS,black cat,black dog",
            "PREFIX_MATCH,hello world,say hello",
            "EXACT_MATCH,hello,hello world",
            "REGEXP,say hallo!,say hullo!"
    })
    void eachKeywordModeAcceptsOnlyItsMatchingMessages(KeywordFilterType type, String matching, String other) {
        var entry = switch (type) {
            case CONTAINS -> KeywordFilterEntry.containsMatch("cat");
            case PREFIX_MATCH -> KeywordFilterEntry.prefixMatch("hello");
            case EXACT_MATCH -> KeywordFilterEntry.exactMatch("hello");
            case REGEXP -> KeywordFilterEntry.regexMatch(Pattern.compile("h[ae]llo"));
        };

        assertThat(entry.test(message("123", "viewer", matching))).isTrue();
        assertThat(entry.test(message("123", "viewer", other))).isFalse();
    }

    @ParameterizedTest(name = "user ID matching: id={0}, name={1}")
    @CsvSource({"123,renamed,true", "456,oldName,false"})
    void userFilteringUsesTheStableIdInsteadOfTheDisplayName(String id, String name, boolean expected) {
        var entry = new UserFilterEntry("123", "oldName", "comment");

        assertThat(entry.test(message(id, name, "hello"))).isEqualTo(expected);
    }

    private static ChatData message(String userId, String userName, String plain) {
        return new ChatData("channel", "channel", "message", userId, userName, userName, "#000000", List.of(),
                new ChatMessage(plain, List.of(ChatMessageFragment.text(plain))), Instant.EPOCH);
    }
}
