package com.github.k7t3.tcv.view.group;

import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupListViewModel;
import com.github.k7t3.tcv.app.group.ChannelGroupOrder;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelGroupViewLifetimeTest {
    @Test
    void aGroupsChannelMenuExposesItsPurposeToAccessibility(FxRobot robot) {
        robot.interact(() -> {
            var channel = mock(com.github.k7t3.tcv.app.channel.TwitchChannelViewModel.class, RETURNS_DEEP_STUBS);
            when(channel.liveProperty()).thenReturn(new javafx.beans.property.ReadOnlyBooleanWrapper().getReadOnlyProperty());
            when(channel.profileImageProperty()).thenReturn(
                    new javafx.beans.property.ReadOnlyObjectWrapper<com.github.k7t3.tcv.app.image.LazyImage>().getReadOnlyProperty());
            when(channel.observableUserName()).thenReturn(new javafx.beans.property.SimpleStringProperty("青空テスト"));
            when(channel.observableStreamTitle()).thenReturn(new javafx.beans.property.SimpleStringProperty("Title"));
            when(channel.getUserName()).thenReturn("青空テスト");
            var group = new ChannelGroup(); group.setName("group"); group.getChannels().add(channel);
            var cell = new ChannelGroupListCell(new AppPreferences(new MemoryPreferences()).getGeneralPreferences(),
                    group, mock(ChannelGroupListViewModel.class), () -> {});
            var window = new Stage();
            try {
                window.setScene(new Scene(new StackPane(cell), 720, 480));
                window.show();
                var tile = (atlantafx.base.controls.Tile) ((javafx.scene.layout.TilePane) cell.getBody()).getChildren().getFirst();
                var menu = (javafx.scene.control.MenuButton) tile.getAction();
                var expected = Resources.getResourceBundle().getLocale().getLanguage().equals("ja")
                        ? "チャンネルメニュー" : "Channel menu";

                assertThat(menu.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT)).isEqualTo(expected);
            } finally { cell.dispose(); window.close(); }
        });
    }

    @Test
    void thePinIconExposesItsPurposeToAccessibility(FxRobot robot) {
        robot.interact(() -> {
            var group = new ChannelGroup(); group.setName("group");
            var cell = new ChannelGroupListCell(new AppPreferences(new MemoryPreferences()).getGeneralPreferences(),
                    group, mock(ChannelGroupListViewModel.class), () -> {});
            var window = new Stage();
            try {
                window.setScene(new Scene(new StackPane(cell), 720, 480));
                window.show();
                var pin = (ToggleButton) ((javafx.scene.Parent) cell.getHeader()).lookup(".toggle-button");

                assertThat(pin.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT))
                        .isEqualTo(Resources.getString("group.pin.tooltip"));
            } finally { cell.dispose(); window.close(); }
        });
    }

    @Test void disposingACellAlsoCancelsItsOpenDeleteConfirmation(FxRobot robot) {
        robot.interact(() -> {
            var group = new ChannelGroup(); group.setName("group");
            var model = mock(ChannelGroupListViewModel.class);
            var cell = new ChannelGroupListCell(new AppPreferences(new MemoryPreferences()).getGeneralPreferences(), group, model, () -> {});
            var window = new Stage(); window.setScene(new Scene(new StackPane(cell), 720, 480)); window.show();
            var stayedOpen = new java.util.concurrent.atomic.AtomicBoolean();
            try {
                javafx.application.Platform.runLater(() -> {
                    cell.dispose();
                    var dialogs = javafx.stage.Window.getWindows().stream().filter(value -> value instanceof Stage stage && stage.getOwner() == window).toList();
                    stayedOpen.set(dialogs.stream().anyMatch(javafx.stage.Window::isShowing));
                    // Red の場合も確認画面の待機を終了し、テストを止めない。
                    dialogs.forEach(value -> ((Stage) value).close());
                });
                ((javafx.scene.control.Button) ((javafx.scene.layout.HBox) cell.getFooter()).getChildren().get(1)).fire();
                assertThat(stayedOpen).isFalse(); verify(model, never()).delete(any());
            } finally { cell.dispose(); window.close(); }
        });
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"pin", "name", "comment"})
    void editingAVisibleGroupStillStartsItsSaveAfterReordering(String field, FxRobot robot) {
        robot.interact(() -> {
            var group = new ChannelGroup(); group.setName("group");
            var source = FXCollections.observableArrayList((javafx.util.Callback<ChannelGroup, javafx.beans.Observable[]>)
                    value -> new javafx.beans.Observable[] {value.pinnedProperty(), value.nameProperty(), value.updatedAtProperty()});
            source.add(group);
            var repository = mock(ChannelGroupRepository.class); when(repository.getAll()).thenReturn(source);
            var task = com.github.k7t3.tcv.app.service.FXTask.task(() -> {}); doReturn(task).when(repository).saveAsync(group);
            var model = new ChannelGroupListViewModel(repository);
            var controller = new ChannelGroupListView(new AppPreferences(new MemoryPreferences()));
            var window = new Stage();
            try {
                var tuple = FluentViewLoader.fxmlView(ChannelGroupListView.class).codeBehind(controller).viewModel(model)
                        .resourceBundle(Resources.getResourceBundle()).load();
                window.setScene(new Scene(new StackPane(tuple.getView()), 720, 480)); window.show();
                tuple.getView().applyCss(); tuple.getView().layout();
                var cell = (ChannelGroupListCell) tuple.getView().lookup(".channel-group-cell"); assertThat(cell).isNotNull();
                if (field.equals("pin")) ((ToggleButton) ((javafx.scene.Parent) cell.getHeader()).lookup(".toggle-button")).fire();
                else {
                    var label = field.equals("name")
                            ? (com.github.k7t3.tcv.view.control.EditableLabel) ((javafx.scene.layout.HBox) cell.getHeader()).getChildren().getFirst()
                            : (com.github.k7t3.tcv.view.control.EditableLabel) cell.getSubHeader();
                    label.setText("changed");
                    label.fireEvent(new com.github.k7t3.tcv.view.control.EditableLabel.EditEvent(label,
                            com.github.k7t3.tcv.view.control.EditableLabel.editCommitEvent(), "changed"));
                }
                verify(repository).saveAsync(group);
                if (field.equals("pin")) assertThat(group.isPinned()).isTrue();
                else if (field.equals("name")) assertThat(group.getName()).isEqualTo("changed");
                else assertThat(group.getComment()).isEqualTo("changed");
            } finally { controller.close(); model.close(); task.cancel(); window.close(); }
        });
    }

    @Test void closingTheViewUnbindsPreferencesAndReopeningUsesTheirCurrentValue(FxRobot robot) {
        robot.interact(() -> {
            var prefs = new AppPreferences(new MemoryPreferences());
            var source = FXCollections.<ChannelGroup>observableArrayList();
            var repository = mock(ChannelGroupRepository.class); when(repository.getAll()).thenReturn(source);
            var model = new ChannelGroupListViewModel(repository);
            var controller = new ChannelGroupListView(prefs);
            var window = new Stage();
            try {
                var tuple = FluentViewLoader.fxmlView(ChannelGroupListView.class).codeBehind(controller).viewModel(model)
                        .resourceBundle(Resources.getResourceBundle()).load();
                window.setScene(new Scene(new StackPane(tuple.getView()), 720, 480)); window.show();
                @SuppressWarnings("unchecked") var combo = (ComboBox<ChannelGroupOrder>) tuple.getView().lookup("#comparatorsComboBox");
                combo.setValue(ChannelGroupOrder.CREATED_AT);
                assertThat(prefs.getStatePreferences().getGroupOrderItem()).isEqualTo("CREATED_AT");
                controller.close(); model.close();
                combo.setValue(ChannelGroupOrder.LIVE_COUNT);
                assertThat(prefs.getStatePreferences().getGroupOrderItem()).isEqualTo("CREATED_AT");
                assertThat(prefs.getStatePreferences().groupOrderItemProperty().isBound()).isFalse();
                assertThat(((javafx.scene.layout.Region) tuple.getView()).maxWidthProperty().isBound()).isFalse();
                prefs.getStatePreferences().setGroupOrderItem("UPDATED_AT");
                var nextModel = new ChannelGroupListViewModel(repository); var next = new ChannelGroupListView(prefs);
                try {
                    var reopened = FluentViewLoader.fxmlView(ChannelGroupListView.class).codeBehind(next).viewModel(nextModel)
                            .resourceBundle(Resources.getResourceBundle()).load();
                    assertThat(((ComboBox<?>) reopened.getView().lookup("#comparatorsComboBox")).getValue()).isEqualTo(ChannelGroupOrder.UPDATED_AT);
                } finally { next.close(); nextModel.close(); }
            } finally { controller.close(); model.close(); window.close(); }
            verify(repository, never()).close();
        });
    }

    @Test void disposingACellDisconnectsItsPinnedPropertyFromTheBorrowedGroup(FxRobot robot) {
        robot.interact(() -> {
            var group = new ChannelGroup(); group.setName("group");
            var model = mock(ChannelGroupListViewModel.class);
            var cell = new ChannelGroupListCell(new AppPreferences(new MemoryPreferences()).getGeneralPreferences(), group, model, () -> {});
            var pin = (ToggleButton) ((javafx.scene.Parent) cell.getHeader()).lookup(".toggle-button");
            cell.dispose(); pin.setSelected(true);
            assertThat(group.isPinned()).isFalse();
            verifyNoInteractions(model);
        });
    }

    @Test void disposingACellReleasesItsChannelTileBindingsAndLiveListener(FxRobot robot) {
        robot.interact(() -> {
            var channel = mock(com.github.k7t3.tcv.app.channel.TwitchChannelViewModel.class, RETURNS_DEEP_STUBS);
            var live = new javafx.beans.property.ReadOnlyBooleanWrapper(true);
            var profile = new javafx.beans.property.ReadOnlyObjectWrapper<com.github.k7t3.tcv.app.image.LazyImage>();
            when(channel.liveProperty()).thenReturn(live.getReadOnlyProperty()); when(channel.isLive()).thenAnswer(call -> live.get());
            when(channel.profileImageProperty()).thenReturn(profile.getReadOnlyProperty());
            when(channel.observableUserName()).thenReturn(new javafx.beans.property.SimpleStringProperty("Name"));
            when(channel.observableStreamTitle()).thenReturn(new javafx.beans.property.SimpleStringProperty("Title"));
            when(channel.getUserName()).thenReturn("Name");
            var group = new ChannelGroup(); group.setName("group"); group.getChannels().add(channel);
            var model = mock(ChannelGroupListViewModel.class);
            var cell = new ChannelGroupListCell(new AppPreferences(new MemoryPreferences()).getGeneralPreferences(), group, model, () -> {});
            var tile = (atlantafx.base.controls.Tile) ((javafx.scene.layout.TilePane) cell.getBody()).getChildren().getFirst();
            var image = (com.github.k7t3.tcv.view.image.LazyImageView) tile.getGraphic();
            cell.dispose(); live.set(false);
            assertThat(image.lazyImageProperty().isBound()).isFalse();
            assertThat(image.getEffect()).isNull(); assertThat(image.getOnMouseEntered()).isNull();
            verify(model, never()).close();
        });
    }

    @Test void closingTheGroupViewDiscardsAnAcceptedPinScroll(FxRobot robot) throws Exception {
        var task = com.github.k7t3.tcv.app.service.FXTask.task(() -> {});
        robot.interact(() -> {
            var group = new ChannelGroup(); group.setName("group");
            var source = FXCollections.observableArrayList((javafx.util.Callback<ChannelGroup, javafx.beans.Observable[]>)
                    value -> new javafx.beans.Observable[] {value.pinnedProperty(), value.nameProperty(), value.updatedAtProperty()});
            source.add(group);
            var repository = mock(ChannelGroupRepository.class); when(repository.getAll()).thenReturn(source); doReturn(task).when(repository).saveAsync(group);
            var model = new ChannelGroupListViewModel(repository);
            var controller = new ChannelGroupListView(new AppPreferences(new MemoryPreferences())); var window = new Stage();
            try {
                var tuple = FluentViewLoader.fxmlView(ChannelGroupListView.class).codeBehind(controller).viewModel(model)
                        .resourceBundle(Resources.getResourceBundle()).load();
                window.setScene(new Scene(new StackPane(tuple.getView()), 720, 480)); window.show(); tuple.getView().applyCss(); tuple.getView().layout();
                var cell = (ChannelGroupListCell) tuple.getView().lookup(".channel-group-cell");
                ((ToggleButton) ((javafx.scene.Parent) cell.getHeader()).lookup(".toggle-button")).fire();
                verify(repository).saveAsync(group);
            } finally { controller.close(); model.close(); window.close(); }
        });
        try (var worker = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) { worker.submit(task).get(5, java.util.concurrent.TimeUnit.SECONDS); }
        robot.interact(() -> assertThat(task.getState()).isEqualTo(javafx.concurrent.Worker.State.SUCCEEDED));
    }
}
