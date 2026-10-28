package com.github.k7t3.tcv.entity.service;

import com.github.k7t3.tcv.database.DBConnector;
import com.github.k7t3.tcv.database.SQLFunction;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ChannelGroupResultMappingTest {
    @Test
    void equalSortKeysMayInterleaveRowsWithoutDuplicatingGroupsOrLosingMembers() throws Exception {
        var time = LocalDateTime.of(2024, 1, 1, 12, 0);
        var first = new ChannelGroupEntity(UUID.randomUUID(), "first", "comment", true, time, time, Set.of("a", "c"));
        var second = new ChannelGroupEntity(UUID.randomUUID(), "second", null, false, time, time, Set.of("b", "d"));
        var empty = new ChannelGroupEntity(UUID.randomUUID(), "empty", "", false, time, time, Set.of());
        // ORDER BY updated_at で同順位になる行の隣接は、SQLの契約に含まれない。
        var rows = List.of(new Row(first, "a"), new Row(second, "b"), new Row(empty, null),
                new Row(first, "c"), new Row(second, "d"));
        var index = new AtomicInteger(-1);
        var result = mock(ResultSet.class);
        when(result.next()).thenAnswer(invocation -> index.incrementAndGet() < rows.size());
        when(result.getString(anyString())).thenAnswer(invocation -> {
            var row = rows.get(index.get());
            return switch ((String) invocation.getArgument(0)) {
                case "id" -> row.group.id().toString();
                case "name" -> row.group.name();
                case "comment" -> row.group.comment();
                case "user_id" -> row.user;
                default -> throw new AssertionError("unexpected column");
            };
        });
        when(result.getBoolean("pinned")).thenAnswer(invocation -> rows.get(index.get()).group.pinned());
        when(result.getTimestamp(anyString())).thenReturn(Timestamp.valueOf(time));
        var connector = mock(DBConnector.class);
        doAnswer(invocation -> {
            Runnable read = invocation.getArgument(0);
            read.run();
            return null;
        }).when(connector).readTransaction(any());
        doAnswer(invocation -> {
            SQLFunction<ResultSet> mapper = invocation.getArgument(1);
            mapper.invoke(result);
            return null;
        }).when(connector).query(anyString(), any());

        assertThat(new ChannelGroupEntityService(connector).retrieveAll()).containsExactly(first, second, empty);
    }

    private record Row(ChannelGroupEntity group, String user) {}
}
