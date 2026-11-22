package com.github.k7t3.tcv.app.group;

import javafx.collections.FXCollections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelGroupEditorLifetimeTest {
    private ChannelGroup group(String name, boolean pinned) {
        var group = new ChannelGroup(); group.setName(name); group.setPinned(pinned); return group;
    }

    @Test void closingTheEditorDetachesItsProjectionAndDoesNotCloseTheBorrowedRepository(FxRobot robot) {
        robot.interact(() -> {
            var source = FXCollections.observableArrayList(group("first", false));
            var repository = mock(ChannelGroupRepository.class);
            when(repository.getAll()).thenReturn(source);
            var model = new ChannelGroupListViewModel(repository);
            model.close();
            source.add(group("late", false));
            assertThat(model.getChannelGroups()).isEmpty();
            verify(repository, never()).close();
            assertThat(source).hasSize(2);
        });
    }

    @Test void clearingTheSortSelectionKeepsPinnedGroupsFirst(FxRobot robot) {
        robot.interact(() -> {
            var first = group("first", false); var pinned = group("pinned", true);
            var repository = mock(ChannelGroupRepository.class);
            when(repository.getAll()).thenReturn(FXCollections.observableArrayList(first, pinned));
            var model = new ChannelGroupListViewModel(repository);
            try { model.setComparator(null); assertThat(model.getChannelGroups()).containsExactly(pinned, first); }
            finally { model.close(); }
        });
    }

    @Test void aClosedEditorCannotStartAnotherWrite(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class);
            when(repository.getAll()).thenReturn(FXCollections.observableArrayList());
            var model = new ChannelGroupListViewModel(repository);
            model.close();
            assertThatThrownBy(() -> model.create("late")).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> model.update(group("late", false))).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> model.delete(group("late", false))).isInstanceOf(IllegalStateException.class);
            verify(repository, never()).saveAsync(any());
            verify(repository, never()).removeAsync(any());
        });
    }
}
