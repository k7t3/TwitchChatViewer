package com.github.k7t3.tcv.domain;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Function;

/** フォロー一覧のページ走査。全ページの成功後に、最初の出現順で重複のないIDを返す。 */
final class FollowedChannelPager {
    record Page(List<String> userIds, String nextCursor) {
        Page { userIds = List.copyOf(userIds); }
    }

    static List<String> load(Function<String, Page> fetch) {
        var ids = new LinkedHashSet<String>();
        var seenCursors = new HashSet<String>();
        String cursor = null;
        while (true) {
            var page = fetch.apply(cursor);
            ids.addAll(page.userIds());
            var next = page.nextCursor();
            if (next == null || next.isBlank()) return List.copyOf(ids);
            if (!seenCursors.add(next)) throw new IllegalStateException("Repeated followed channels pagination cursor");
            cursor = next;
        }
    }
}
