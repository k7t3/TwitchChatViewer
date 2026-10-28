package com.github.k7t3.tcv.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class FollowedChannelPagerTest {
    @Test
    void manyPagesDoNotUseTheCallStackOrAnArbitraryPageLimit() {
        var calls = new AtomicInteger();
        var result = FollowedChannelPager.load(cursor -> {
            var index = calls.incrementAndGet();
            assertThat(cursor).isEqualTo(index == 1 ? null : "page-" + index);
            return new FollowedChannelPager.Page(List.of("user-" + index), index == 6000 ? null : "page-" + (index+1));
        });
        assertThat(calls).hasValue(6000);
        assertThat(result).containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1,6000).mapToObj(i -> "user-"+i).toList());
    }

    @Test
    void aLongerCursorCycleFailsBeforeFetchingItsFirstCursorAgain() {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> FollowedChannelPager.load(cursor -> switch (calls.incrementAndGet()) {
            case 1 -> new FollowedChannelPager.Page(List.of("1"), "a");
            case 2 -> new FollowedChannelPager.Page(List.of("2"), "b");
            default -> new FollowedChannelPager.Page(List.of("3"), "a");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("Repeated followed channels pagination cursor");
        assertThat(calls).hasValue(3);
    }
}
