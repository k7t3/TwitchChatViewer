package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.*;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.domain.channel.StreamInfo;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.k7t3.tcv.view.core.FloatableStage;
import com.github.k7t3.tcv.view.core.WindowBounds;
import com.github.k7t3.tcv.view.image.LazyImageView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatRoomViewLifetimeTest {
    @org.junit.jupiter.api.Test
    void aMergedMembersMenuExposesItsCurrentChannelNameUntilReleased(FxRobot robot) {
        robot.interact(() -> {
            try (var fixture = new Fixture("merged")) {
                var menu = (MenuButton) ((ToolBar) fixture.root.lookup("#profileImageContainer")).getItems().getFirst();
                var name = (StringProperty) fixture.channel.observableUserName();

                assertThat(menu.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT)).isEqualTo("Channel");
                name.set("青空テスト");
                assertThat(menu.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT)).isEqualTo("青空テスト");
                fixture.closeView();
                name.set("Released");
                assertThat(menu.accessibleTextProperty().isBound()).isFalse();
                assertThat(menu.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT)).isEqualTo("青空テスト");
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"single", "merged"})
    void theAutoscrollIconExposesItsPurposeToAccessibility(String type, FxRobot robot) {
        robot.interact(() -> {
            try (var fixture = new Fixture(type)) {
                var scrolling = (ToggleButton) fixture.root.lookup("#scrollToEnd");

                assertThat(scrolling.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT))
                        .isEqualTo(Resources.getString("chat.scroll"));
            }
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"single", "merged", "floatingSingle", "floatingMerged"})
    void everyChatActionsMenuExposesItsPurposeToAccessibility(String type, FxRobot robot) {
        robot.interact(() -> {
            try (var fixture = new Fixture(type)) {
                var menu = (MenuButton) fixture.root.lookup(type.startsWith("floating")
                        ? "#menuButton" : "#actionsMenuButton");
                var expected = Resources.getResourceBundle().getLocale().getLanguage().equals("ja")
                        ? "チャットメニュー" : "Chat menu";

                assertThat(menu.queryAccessibleAttribute(javafx.scene.AccessibleAttribute.TEXT))
                        .isEqualTo(expected);
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"single", "merged", "floatingSingle", "floatingMerged"})
    void closingAnyChatViewAlsoClosesItsGroupMenu(String type, FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture(type)) {
                var button = (MenuButton) f.root.lookup(type.startsWith("floating") ? "#menuButton" : "#actionsMenuButton");
                var menu = (com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu) button.getItems().stream()
                        .filter(com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu.class::isInstance).findFirst().orElseThrow();
                assertThat(menu.getItems()).hasSize(2);
                f.closeView(); menu.refreshItems();
                assertThat(menu.getItems()).isEmpty(); assertThat(menu.getOnShowing()).isNull();
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"merged", "floatingMerged"})
    void aMergedRoomsGroupMenuTracksMembersAddedAfterTheViewWasBuilt(String type, FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture(type)) {
                var group = new com.github.k7t3.tcv.app.group.ChannelGroup(); group.setName("Group"); group.getChannels().add(f.channel);
                f.groups.getAll().add(group);
                var button = (MenuButton) f.root.lookup(type.startsWith("floating") ? "#menuButton" : "#actionsMenuButton");
                var menu = (com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu) button.getItems().stream()
                        .filter(com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu.class::isInstance).findFirst().orElseThrow();
                menu.refreshItems(); var item = (CheckMenuItem) menu.getItems().getLast(); assertThat(item.isSelected()).isTrue();
                var added = mock(TwitchChannelViewModel.class, org.mockito.AdditionalAnswers.delegatesTo(f.channel));
                f.merged.getChannels().put(added, f.single);
                assertThat(item.isSelected()).isFalse();
                group.getChannels().add(added); assertThat(item.isSelected()).isTrue();
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"single", "merged", "floatingSingle", "floatingMerged"})
    void closingAnyChatViewDetachesAutoscrollAndRemovesItsFlowWithoutLeavingBorrowedChat(String type, FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture(type)) {
                var scrolling = f.autoScrollControl();
                f.room.autoScrollProperty().set(false); assertThat(scrolling.get()).isFalse();
                var flow = f.root.lookupAll("*").stream().filter(VirtualizedScrollPane.class::isInstance).findFirst().orElseThrow();
                f.closeView(); f.closeView();
                f.room.autoScrollProperty().set(true); assertThat(scrolling.get()).isFalse();
                assertThat(flow.getParent()).isNull();
                verify(f.room, never()).leaveChatAsync();
                if (f.stage instanceof FloatableStage) {
                    assertThat(f.stage.isShowing()).isFalse(); assertThat(f.stage.getScene()).isNull();
                }
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"single", "merged", "floatingSingle", "floatingMerged"})
    void closedProfilesAndBackgroundsNoLongerFollowTheirBorrowedChannel(String type, FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture(type)) {
                var image = f.root.lookupAll("*").stream().filter(LazyImageView.class::isInstance).map(LazyImageView.class::cast).findFirst().orElseThrow();
                assertThat(image.getEffect()).isNull();
                f.closeView(); f.live.set(false);
                assertThat(image.getEffect()).isNull(); assertThat(image.getLazyImage()).isNull();
                assertThat(image.lazyImageProperty().isBound()).isFalse(); assertThat(image.imageProperty().isBound()).isFalse();
                var background = (javafx.scene.layout.Pane) f.root.lookup("#backgroundImageLayer");
                assertThat(background.backgroundProperty().isBound()).isFalse();
                assertThat(image.getClip()).isNull();
            }
        });
    }

    @org.junit.jupiter.api.Test
    void aClosedSingleChatStopsReceivingRoomStateIcons(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture("single")) {
                var states = (javafx.scene.layout.Pane) f.root.lookup("#stateContainer");
                f.closeView();
                f.single.getRoomStates().add(ChatRoomState.EMOTE_ONLY);
                assertThat(states.getChildren()).isEmpty();
            }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void removingAChatOrClosingItsParentAlsoReleasesTheChildView(boolean closeParent, FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture("container")) {
                var scrolling = f.autoScrollControl();
                f.room.autoScrollProperty().set(false); assertThat(scrolling.get()).isFalse();
                if (closeParent) f.closeView(); else f.container.getChatRoomList().remove(f.single);
                f.room.autoScrollProperty().set(true); assertThat(scrolling.get()).isFalse();
                verify(f.single, never()).leaveChatAsync();
            }
        });
    }

    @org.junit.jupiter.api.Test
    void theContainerAlsoDisplaysChatsThatExistedBeforeItsViewLoaded(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture("containerInitial")) {
                assertThat(f.root.lookup("#scrollToEnd")).isNotNull();
                assertThat(((javafx.scene.control.Label) f.root.lookup("#userNameLabel")).getText()).isEqualTo("Channel");
            }
        });
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.CsvSource({"floatingSingle,false", "floatingSingle,true", "floatingMerged,false", "floatingMerged,true"})
    void hidingOrRequestingCloseReleasesTheFloatingViewAndOnlyUserCloseLeavesTheChat(String type, boolean requestClose, FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture(type)) {
                if (requestClose) ((FloatableStage) f.stage).requestClose(); else f.stage.hide();
                assertThat(f.stage.isShowing()).isFalse(); assertThat(f.stage.getScene()).isNull();
                if (requestClose) verify(f.room).leaveChatAsync(); else verify(f.room, never()).leaveChatAsync();
                ((FloatableStage) f.stage).requestClose();
                if (requestClose) verify(f.room, times(1)).leaveChatAsync(); else verify(f.room, never()).leaveChatAsync();
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"merged", "floatingMerged"})
    void removingAMergedMemberDetachesItsOldProfile(String type, FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture(type)) {
                var image = f.root.lookupAll("*").stream().filter(LazyImageView.class::isInstance).map(LazyImageView.class::cast).findFirst().orElseThrow();
                f.merged.getChannels().remove(f.channel); f.live.set(false);
                assertThat(image.getEffect()).isNull(); assertThat(image.getLazyImage()).isNull();
                assertThat(((ToolBar) f.root.lookup("#profileImageContainer")).getItems()).isEmpty();
            }
        });
    }

    @org.junit.jupiter.api.Test
    void normalRoomStateHasNoIconAndCanCoexistWithRestrictedStates(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture("single")) {
                var states = (javafx.scene.layout.Pane) f.root.lookup("#stateContainer");
                f.single.getRoomStates().add(ChatRoomState.NORMAL); assertThat(states.getChildren()).isEmpty();
                f.single.getRoomStates().add(ChatRoomState.EMOTE_ONLY); assertThat(states.getChildren()).hasSize(1);
                f.single.getRoomStates().remove(ChatRoomState.NORMAL); assertThat(states.getChildren()).hasSize(1);
            }
        });
    }

    @org.junit.jupiter.api.Test
    void closingTheParentDuringChildInitializationStopsFurtherProfileRequests(FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture("containerReentrant")) {
                verify(f.channel, never()).profileImageProperty();
                assertThat(f.root.lookup("#userNameLabel")).isNull();
                verify(f.single, never()).joinChatAsync();
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"floatingSingle", "floatingMerged"})
    void closedFloatingControlsCannotKeepWritingSharedWindowPreferences(String type, FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture(type)) {
                var stage = (FloatableStage) f.stage; var prefs = f.preferences.getChatPreferences();
                var previous = stage.getBackgroundOpacity(); f.closeView();
                prefs.floatingChatOpacityProperty().set(0.2); assertThat(stage.getBackgroundOpacity()).isEqualTo(previous);
                stage.setBackgroundOpacity(0.8); assertThat(prefs.floatingChatOpacityProperty().get()).isEqualTo(0.2);
                var wasOnTop = stage.isAlwaysOnTop(); prefs.floatingChatAlwaysTopProperty().set(!wasOnTop);
                assertThat(stage.isAlwaysOnTop()).isEqualTo(wasOnTop);
            }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"floatingSingle", "floatingMerged"})
    void restoringTheFloatingViewReleasesOnlyTheViewAndRequestsOneRestore(String type, FxRobot robot) {
        robot.interact(() -> {
            try (var f = new Fixture(type)) {
                var menu = (MenuButton) f.root.lookup("#menuButton");
                menu.getItems().stream().filter(item -> "restoreMenuItem".equals(item.getId())).findFirst().orElseThrow().fire();
                assertThat(f.stage.isShowing()).isFalse(); assertThat(f.stage.getScene()).isNull();
                verify(f.room).restoreToContainer(); verify(f.room, never()).leaveChatAsync();
            }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final SimpleBooleanProperty live = new SimpleBooleanProperty(true);
        final TwitchChannelViewModel channel = mock(TwitchChannelViewModel.class);
        final SingleChatRoomViewModel single = configure(mock(SingleChatRoomViewModel.class));
        final MergedChatRoomViewModel merged = configure(mock(MergedChatRoomViewModel.class));
        final ChatRoomContainerViewModel container = mock(ChatRoomContainerViewModel.class);
        final AppPreferences preferences = new AppPreferences(new MemoryPreferences());
        final ChannelGroupRepository groups = mock(ChannelGroupRepository.class);
        final ChatRoomViewModel room;
        final Parent root;
        final AutoCloseable controller;
        final Stage stage;
        Fixture(String type) {
            when(channel.liveProperty()).thenReturn(live); when(channel.isLive()).thenAnswer(ignored -> live.get());
            when(channel.profileImageProperty()).thenReturn(new ReadOnlyObjectWrapper<LazyImage>().getReadOnlyProperty());
            when(channel.offlineImageProperty()).thenReturn(new ReadOnlyObjectWrapper<Image>().getReadOnlyProperty());
            when(channel.observableUserName()).thenReturn(new SimpleStringProperty("Channel"));
            when(channel.observableStreamTitle()).thenReturn(new SimpleStringProperty("Live"));
            when(channel.getUserName()).thenReturn("Channel"); when(channel.getStreamTitle()).thenReturn("Live");
            when(channel.getStreamInfo()).thenReturn(new StreamInfo("123", "game", "Game", "Live", List.of(), 10, LocalDateTime.now(), Duration.ZERO, "ja"));
            when(single.getChannel()).thenReturn(channel); when(single.getRoomStates()).thenReturn(FXCollections.<ChatRoomState>observableSet());
            when(merged.getChannels()).thenReturn(FXCollections.observableHashMap()); merged.getChannels().put(channel, single);
            when(container.getChatRoomList()).thenReturn(FXCollections.observableArrayList());
            when(container.getFloatingChatRoomList()).thenReturn(FXCollections.observableArrayList());
            when(container.loadingProperty()).thenReturn(new ReadOnlyBooleanWrapper().getReadOnlyProperty());
            when(container.errorProperty()).thenReturn(new ReadOnlyStringWrapper("").getReadOnlyProperty());
            when(container.selectingCountProperty()).thenReturn(new ReadOnlyIntegerWrapper().getReadOnlyProperty());
            when(container.selectModeProperty()).thenReturn(new ReadOnlyBooleanWrapper().getReadOnlyProperty());
            when(groups.getAll()).thenReturn(FXCollections.observableArrayList());
            var bounds = mock(WindowBoundsService.class);
            when(bounds.getBoundsAsync(anyString())).thenAnswer(ignored -> FXTask.task(() -> new WindowBounds(0, 0, 360, 640, false)));
            switch (type) {
                case "container", "containerInitial", "containerReentrant" -> {
                    if (type.equals("containerInitial")) container.getChatRoomList().add(single);
                    var tuple = FluentViewLoader.fxmlView(ChatContainerView.class).codeBehind(new ChatContainerView(groups, preferences.getChatPreferences(), () -> bounds)).viewModel(container).resourceBundle(Resources.getResourceBundle()).load();
                    root = tuple.getView(); controller = tuple.getCodeBehind(); room = single; stage = new Stage();
                    if (type.equals("containerReentrant")) {
                        when(single.getChannel()).thenAnswer(ignored -> { tuple.getCodeBehind().close(); return channel; });
                        container.getChatRoomList().add(single);
                    }
                    if (type.equals("container")) container.getChatRoomList().add(single);
                }
                case "single" -> {
                    var tuple = FluentViewLoader.fxmlView(SingleChatRoomView.class).codeBehind(new SingleChatRoomView(groups)).viewModel(single).resourceBundle(Resources.getResourceBundle()).load();
                    root = tuple.getView(); controller = tuple.getCodeBehind(); room = single; stage = new Stage();
                }
                case "merged" -> {
                    var tuple = FluentViewLoader.fxmlView(MergedChatRoomView.class).codeBehind(new MergedChatRoomView(groups)).viewModel(merged).resourceBundle(Resources.getResourceBundle()).load();
                    root = tuple.getView(); controller = tuple.getCodeBehind(); room = merged; stage = new Stage();
                }
                case "floatingSingle" -> {
                    var tuple = FluentViewLoader.fxmlView(FloatableSingleChatRoomView.class).codeBehind(new FloatableSingleChatRoomView(groups, preferences.getChatPreferences(), bounds)).viewModel(single).resourceBundle(Resources.getResourceBundle()).load();
                    root = tuple.getView(); controller = tuple.getCodeBehind(); room = single; stage = tuple.getCodeBehind().getFloatableStage();
                }
                case "floatingMerged" -> {
                    var tuple = FluentViewLoader.fxmlView(FloatableMergedChatRoomView.class).codeBehind(new FloatableMergedChatRoomView(groups, preferences.getChatPreferences(), bounds)).viewModel(merged).resourceBundle(Resources.getResourceBundle()).load();
                    root = tuple.getView(); controller = tuple.getCodeBehind(); room = merged; stage = tuple.getCodeBehind().getFloatableStage();
                }
                default -> throw new AssertionError(type);
            }
            if (stage instanceof FloatableStage floating) floating.setContent(root);
            else stage.setScene(new Scene(new StackPane(root), 400, 300));
            stage.show(); root.applyCss(); root.layout();
        }
        BooleanProperty autoScrollControl() {
            if (!(stage instanceof FloatableStage)) return ((ToggleButton) root.lookup("#scrollToEnd")).selectedProperty();
            return ((CheckMenuItem) ((MenuButton) root.lookup("#menuButton")).getItems().stream()
                    .filter(item -> "autoScrollMenuItem".equals(item.getId())).findFirst().orElseThrow()).selectedProperty();
        }
        void closeView() {
            try { controller.close(); } catch (Exception failure) { throw new RuntimeException(failure); }
        }
        @Override public void close() { closeView(); stage.close(); stage.setScene(null); }
        private static <T extends ChatRoomViewModel> T configure(T room) {
            when(room.getIdentity()).thenReturn("room"); when(room.getChatDataList()).thenReturn(FXCollections.observableArrayList());
            when(room.autoScrollProperty()).thenReturn(new SimpleBooleanProperty(true)); when(room.selectedProperty()).thenReturn(new SimpleBooleanProperty());
            when(room.selectModeProperty()).thenReturn(new SimpleBooleanProperty()); return room;
        }
    }
}
