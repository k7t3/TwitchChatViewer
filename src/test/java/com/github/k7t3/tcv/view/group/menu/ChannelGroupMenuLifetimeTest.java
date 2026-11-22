package com.github.k7t3.tcv.view.group.menu;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.scene.control.ContextMenu;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelGroupMenuLifetimeTest {
    @Test
    void closingTheMenuDetachesTheCallersSelectionBinding(FxRobot robot) {
        robot.interact(() -> {
            var selected = FXCollections.<TwitchChannelViewModel>observableArrayList();
            var menu = new ChannelGroupMenu(mock(ChannelGroupRepository.class), selected);
            try {
                menu.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(selected));
                assertThat(menu.isDisable()).isTrue();
                menu.close(); menu.close();
                assertThat(menu.disableProperty().isBound()).isFalse();
                selected.add(mock(TwitchChannelViewModel.class));
                assertThat(menu.isDisable()).isTrue();
            } finally { menu.close(); }
        });
    }

    @Test
    void theFirstRealSubmenuOpeningBuildsTheCurrentGroups(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class);
            when(repository.getAll()).thenReturn(FXCollections.observableArrayList(group()));
            var menu = new ChannelGroupMenu(repository, FXCollections.observableArrayList());
            var popup = new ContextMenu(menu); var stage = new javafx.stage.Stage();
            stage.setScene(new javafx.scene.Scene(new javafx.scene.layout.StackPane(), 300, 200));
            try {
                stage.show(); popup.show(stage, stage.getX() + 20, stage.getY() + 20); menu.show();
                assertThat(menu.isShowing()).isTrue(); assertThat(menu.getItems()).hasSize(3);
                assertThat(menu.getItems().getLast().getText()).isEqualTo("Group");
                menu.close(); assertThat(menu.isShowing()).isFalse();
            } finally { menu.close(); popup.hide(); stage.close(); stage.setScene(null); }
        });
    }

    @Test
    void anEmptySelectionCannotRewriteOrRemoveAnUnrelatedGroup(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var group = group();
            var item = new ChannelGroupMenuItem(repository, FXCollections.observableArrayList(), group);
            try {
                item.fire(); verify(repository, never()).saveAsync(any()); verify(repository, never()).removeAsync(any());
            } finally { item.close(); }
        });
    }

    @Test
    void changingSelectionDuringAMembershipUpdateCannotChangeTheAcceptedAction(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var group = group();
            var a = mock(TwitchChannelViewModel.class); var b = mock(TwitchChannelViewModel.class);
            var selected = FXCollections.observableArrayList(a, b);
            group.getChannels().addListener((javafx.collections.SetChangeListener<TwitchChannelViewModel>) change -> selected.clear());
            var item = new ChannelGroupMenuItem(repository, selected, group);
            try {
                item.setSelected(true); item.fire();
                assertThat(group.getChannels()).containsExactlyInAnyOrder(a, b); verify(repository).saveAsync(group);
            } finally { item.close(); }
        });
    }

    @Test
    void membershipFollowsBothTheSelectedChannelsAndTheGroup(FxRobot robot) {
        robot.interact(() -> {
            var a = mock(TwitchChannelViewModel.class); var b = mock(TwitchChannelViewModel.class);
            var selected = FXCollections.observableArrayList(a);
            var group = group(); group.getChannels().add(a);
            var item = new ChannelGroupMenuItem(mock(ChannelGroupRepository.class), selected, group);
            try {
                assertThat(item.isSelected()).isTrue();
                selected.add(b); assertThat(item.isSelected()).isFalse();
                group.getChannels().add(b); assertThat(item.isSelected()).isTrue();
                group.getChannels().remove(a); assertThat(item.isSelected()).isFalse();
            } finally { item.close(); }
        });
    }

    @Test
    void closingAnItemDetachesItsBorrowedGroupAndRetainedAction(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var group = group();
            var a = mock(TwitchChannelViewModel.class);
            var item = new ChannelGroupMenuItem(repository, FXCollections.observableArrayList(a), group);
            item.close(); item.close();
            assertThat(item.textProperty().isBound()).isFalse();
            group.setName("Later"); group.getChannels().add(a);
            assertThat(item.getText()).isEqualTo("Group"); assertThat(item.isSelected()).isFalse();
            group.getChannels().clear(); item.setSelected(true); item.fire();
            assertThat(group.getChannels()).isEmpty(); verify(repository, never()).saveAsync(any());
        });
    }

    @Test
    void refreshingTheMenuClosesItsReplacedItems(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var group = group();
            when(repository.getAll()).thenReturn(FXCollections.observableArrayList(group));
            var a = mock(TwitchChannelViewModel.class);
            var menu = new ChannelGroupMenu(repository, FXCollections.observableArrayList(a));
            try {
                menu.refreshItems(); var old = menu.getItems().getLast();
                menu.refreshItems(); var current = menu.getItems().getLast();
                group.setName("Later");
                assertThat(old.textProperty().isBound()).isFalse(); assertThat(old.getText()).isEqualTo("Group");
                assertThat(current.getText()).isEqualTo("Later");
                ((javafx.scene.control.CheckMenuItem) old).setSelected(true); old.fire();
                assertThat(group.getChannels()).isEmpty(); verify(repository, never()).saveAsync(any());
            } finally { menu.close(); }
        });
    }

    @Test
    void aClosedMenuDoesNotRefreshFromItsFormerPopupOrExplicitCalls(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class);
            when(repository.getAll()).thenReturn(FXCollections.observableArrayList(group()));
            var menu = new ChannelGroupMenu(repository, FXCollections.observableArrayList());
            var popup = new ContextMenu(menu);
            Event.fireEvent(menu, new Event(javafx.scene.control.Menu.ON_SHOWING));
            assertThat(menu.getItems()).hasSize(3);
            var items = List.copyOf(menu.getItems());
            menu.close(); menu.close(); clearInvocations(repository);
            Event.fireEvent(popup, new WindowEvent(popup, WindowEvent.WINDOW_SHOWING)); menu.refreshItems();
            assertThat(menu.getItems()).isEmpty(); verify(repository, never()).getAll();
            for (var item : items) if (item instanceof javafx.scene.control.CheckMenuItem) item.fire();
            verify(repository, never()).saveAsync(any()); verify(repository, never()).removeAsync(any());
            popup.getItems().clear();
        });
    }

    private static ChannelGroup group() { var group = new ChannelGroup(); group.setName("Group"); return group; }
}
