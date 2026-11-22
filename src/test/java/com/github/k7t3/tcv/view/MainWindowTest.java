package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.view.MainViewFactory;

import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;

import com.github.k7t3.tcv.app.service.LiveStateNotificator;

import com.github.k7t3.tcv.view.MainModelFactory;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomViewModel;
import com.github.k7t3.tcv.app.chat.MergedChatRoomViewModel;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.app.theme.ThemeManager;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.database.DatabaseVersion;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.k7t3.tcv.view.main.MainView;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.MenuButton;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.io.IOException;
import java.awt.Desktop;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class MainWindowTest {
    @ParameterizedTest
    @ValueSource(strings = {"chats", "followers", "groups", "clips", "preferences", "search", "about", "links"})
    void aComponentReturnedAfterMainViewCloseIsReleased(String boundary, FxRobot robot) throws Exception {
        var view = new AtomicReference<MainView>();
        var late = new AtomicReference<AutoCloseable>();
        var created = new ArrayList<AutoCloseable>();
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(false, false, f -> new MainView(f.preferences,
                        mock(com.github.k7t3.tcv.domain.auth.CredentialStore.class), new MainView.Components(
                        f.helper::getKeyBindingCombinations,
                        pane -> returnedAfterClose("groups", boundary, enabled(mock(com.github.k7t3.tcv.view.command.OpenChannelGroupCommand.class)), view, late, created),
                        pane -> returnedAfterClose("clips", boundary, enabled(mock(com.github.k7t3.tcv.view.command.OpenClipCommand.class)), view, late, created),
                        () -> returnedAfterClose("preferences", boundary, enabled(mock(com.github.k7t3.tcv.view.command.OpenPreferencesCommand.class)), view, late, created),
                        pane -> returnedAfterClose("search", boundary, enabled(mock(com.github.k7t3.tcv.view.command.OpenSearchChannelCommand.class)), view, late, created),
                        () -> returnedAfterClose("about", boundary, enabled(mock(com.github.k7t3.tcv.view.command.OpenAboutCommand.class)), view, late, created),
                        () -> returnedAfterClose("links", boundary, mock(com.github.k7t3.tcv.app.service.BrowserActions.class), view, late, created),
                        () -> returnedAfterClose("followers", boundary, mock(com.github.k7t3.tcv.view.channel.TwitchChannelListView.class), view, late, created),
                        () -> returnedAfterClose("chats", boundary, mock(com.github.k7t3.tcv.view.chat.ChatContainerView.class), view, late, created))));
                reference.set(fixture);
                view.set(fixture.controller);
                fixture.window.show();
            });

            assertThat(late.get()).as("component returned after close").isNotNull();
            verify(late.get()).close();
        } finally {
            try { close(reference.get(), robot); }
            finally {
                robot.interact(() -> {
                    for (var component : created) {
                        try { component.close(); }
                        catch (Exception failure) { throw new AssertionError(failure); }
                    }
                });
            }
        }
    }

    private static <T extends com.github.k7t3.tcv.app.service.BasicCommand> T enabled(T command) {
        when(command.notExecutableProperty()).thenReturn(new javafx.beans.property.ReadOnlyBooleanWrapper(false).getReadOnlyProperty());
        return command;
    }

    private static <T extends AutoCloseable> T returnedAfterClose(String boundary, String selected, T component,
                                                                  AtomicReference<MainView> view, AtomicReference<AutoCloseable> late,
                                                                  ArrayList<AutoCloseable> created) {
        created.add(component);
        if (boundary.equals(selected)) {
            late.set(component);
            view.get().close();
        }
        return component;
    }
    @Test
    void aFailedTermsBrowserActionReportsToTheMainWindowThatStartedIt(FxRobot robot) throws Exception {
        var reference = new AtomicReference<Fixture>();
        var errors = new AtomicReference<MockedStatic<ExceptionHandler>>();
        var notified = new CountDownLatch(1);
        var failure = new UnsupportedOperationException("browser unavailable");
        try {
            robot.interact(() -> {
                var fixture = new Fixture();
                reference.set(fixture);
                fixture.window.show();
                var handler = mockStatic(ExceptionHandler.class);
                errors.set(handler);
                handler.when(() -> ExceptionHandler.handle(any(Stage.class), any(Throwable.class))).thenAnswer(call -> {
                    notified.countDown(); return null;
                });
                var menu = (MenuButton) fixture.stage.getScene().lookup("#userMenuButton");

                menu.getItems().stream().filter(item -> "termsMenuItem".equals(item.getId())).findFirst().orElseThrow().fire();

                assertThat(fixture.queue).hasSize(1);
                try (var desktop = mockStatic(Desktop.class)) {
                    desktop.when(Desktop::getDesktop).thenThrow(failure);
                    fixture.queue.remove().run();
                    desktop.verify(Desktop::getDesktop);
                }
            });
            assertThat(notified.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> {
                errors.get().verify(() -> ExceptionHandler.handle(reference.get().stage, failure));
                errors.get().verifyNoMoreInteractions();
                assertThat(reference.get().stage.isShowing()).isTrue();
            });
        } finally {
            try { close(reference.get(), robot); }
            finally { robot.interact(() -> { if (errors.get() != null) errors.get().close(); }); }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"single", "merged", "floatingSingle", "floatingMerged"})
    void chatViewsUseTheMainWindowsServicesInsteadOfGlobalServices(String type, FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                var single = singleChat();
                var group = new ChannelGroup();
                group.setName("Parent group");
                group.getChannels().add(single.getChannel());
                fixture.helper.getChannelGroupRepository().getAll().add(group);
                ChatRoomViewModel room = single;
                if (type.toLowerCase(java.util.Locale.ROOT).contains("merged")) {
                    var merged = configureChat(mock(MergedChatRoomViewModel.class));
                    when(merged.getChannels()).thenReturn(FXCollections.observableHashMap());
                    merged.getChannels().put(single.getChannel(), single);
                    room = merged;
                }
                var floating = type.startsWith("floating");
                fixture.preferences.getChatPreferences().floatingChatOpacityProperty().set(0.42);
                var bounds = mock(WindowBoundsService.class);
                when(bounds.getBoundsAsync("services-room")).thenReturn(com.github.k7t3.tcv.app.service.FXTask.task(
                        () -> new com.github.k7t3.tcv.view.core.WindowBounds(70, 80, 400, 500, false)));
                doReturn(bounds).when(fixture.helper).getWindowBoundsService();
                (floating ? fixture.model.getChatContainer().getFloatingChatRoomList()
                        : fixture.model.getChatContainer().getChatRoomList()).add(room);
                try (var global = mockConstruction(AppHelper.class); var preferences = mockConstruction(AppPreferences.class)) {

                    fixture.window.show();

                    var stage = floating ? javafx.stage.Window.getWindows().stream()
                            .filter(window -> window instanceof com.github.k7t3.tcv.view.core.FloatableStage && window.isShowing())
                            .map(com.github.k7t3.tcv.view.core.FloatableStage.class::cast).findFirst().orElseThrow() : fixture.stage;
                    var actions = (MenuButton) stage.getScene().lookup(floating ? "#menuButton" : "#actionsMenuButton");
                    assertThat(actions).isNotNull();
                    var menu = (com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu) actions.getItems().stream()
                            .filter(com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu.class::isInstance).findFirst().orElseThrow();
                    menu.refreshItems();
                    assertThat(menu.getItems()).hasSize(3);
                    var membership = (javafx.scene.control.CheckMenuItem) menu.getItems().getLast();
                    assertThat(membership.getText()).isEqualTo("Parent group");
                    assertThat(membership.isSelected()).isTrue();
                    if (floating) {
                        assertThat(((com.github.k7t3.tcv.view.core.FloatableStage) stage).getBackgroundOpacity()).isEqualTo(0.42);
                        verify(bounds).getBoundsAsync("services-room");
                    }
                    assertThat(fixture.failures).isEmpty();
                    assertThat(global.constructed()).isEmpty();
                    assertThat(preferences.constructed()).isEmpty();
                } finally {
                    fixture.model.getChatContainer().getChatRoomList().clear();
                    fixture.model.getChatContainer().getFloatingChatRoomList().clear();
                }
            });
        } finally { close(reference.get(), robot); }
    }

    private static SingleChatRoomViewModel singleChat() {
        var channel = mock(TwitchChannelViewModel.class);
        var live = new SimpleBooleanProperty(true);
        when(channel.liveProperty()).thenReturn(live);
        when(channel.isLive()).thenAnswer(ignored -> live.get());
        when(channel.profileImageProperty()).thenReturn(new ReadOnlyObjectWrapper<LazyImage>().getReadOnlyProperty());
        when(channel.observableUserName()).thenReturn(new SimpleStringProperty("Channel"));
        when(channel.observableStreamTitle()).thenReturn(new SimpleStringProperty("Live"));
        when(channel.getStreamInfo()).thenReturn(new com.github.k7t3.tcv.domain.channel.StreamInfo(
                "123", "game", "Game", "Live", java.util.List.of(), 10,
                java.time.LocalDateTime.of(2026, 10, 21, 12, 0), java.time.Duration.ZERO, "ja"));
        var room = configureChat(mock(SingleChatRoomViewModel.class));
        when(room.getChannel()).thenReturn(channel);
        when(room.getRoomStates()).thenReturn(FXCollections.observableSet());
        return room;
    }

    private static <T extends ChatRoomViewModel> T configureChat(T room) {
        when(room.getIdentity()).thenReturn("services-room");
        when(room.getChatDataList()).thenReturn(FXCollections.observableArrayList());
        when(room.autoScrollProperty()).thenReturn(new SimpleBooleanProperty(true));
        when(room.selectedProperty()).thenReturn(new SimpleBooleanProperty());
        when(room.selectModeProperty()).thenReturn(new SimpleBooleanProperty());
        return room;
    }

    @Test
    void aClosedMainViewCannotStartAnotherInitialAuthentication(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        var authentications = new ArrayList<AuthenticatorViewModel>();
        try {
            robot.interact(() -> {
                var f = new Fixture(false, true); reference.set(f); f.window.show();
                stubAuthentication(f, authentications);
                doCallRealMethod().when(f.controller).startMainView();
                f.window.close(); f.controller.startMainView();
                verify(f.model, never()).createAuthenticator(any());
            });
        } finally { robot.interact(() -> authentications.forEach(AuthenticatorViewModel::close)); close(reference.get(), robot); }
    }

    @Test
    void repeatedInitialStartupSharesTheSameAuthenticationCommand(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        var authentications = new ArrayList<AuthenticatorViewModel>();
        try {
            robot.interact(() -> {
                var f = new Fixture(false, true); reference.set(f); f.window.show();
                stubAuthentication(f, authentications);
                doCallRealMethod().when(f.controller).startMainView();
                f.controller.startMainView(); f.controller.startMainView();
                verify(f.model, times(1)).createAuthenticator(any());
                f.window.close(); assertThat(authentications.getFirst().isClosed()).isTrue();
            });
        } finally { robot.interact(() -> authentications.forEach(AuthenticatorViewModel::close)); close(reference.get(), robot); }
    }

    @Test
    void closingTheWindowAlsoEndsAnAuthenticationMenuActionCapturedBeforeClose(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        var authentications = new ArrayList<AuthenticatorViewModel>();
        try {
            robot.interact(() -> {
                var f = new Fixture(false, true); reference.set(f); f.window.show(); stubAuthentication(f, authentications);
                var menu = (javafx.scene.control.MenuButton) f.stage.getScene().lookup("#userMenuButton");
                var action = menu.getItems().getLast().getOnAction();
                f.window.close(); action.handle(new javafx.event.ActionEvent());
                verify(f.model, never()).createAuthenticator(any());
            });
        } finally { robot.interact(() -> authentications.forEach(AuthenticatorViewModel::close)); close(reference.get(), robot); }
    }

    private void stubAuthentication(Fixture f, ArrayList<AuthenticatorViewModel> models) {
        doAnswer(call -> {
            var model = spy(new AuthenticatorViewModel(
                    mock(com.github.k7t3.tcv.domain.auth.CredentialStore.class), client -> {}, Runnable::run, unexpectedBrowserFailure -> { throw new AssertionError(unexpectedBrowserFailure); }));
            doNothing().when(model).beginAuthentication(); models.add(model); return model;
        }).when(f.model).createAuthenticator(any());
    }

    @Test
    void closingTheMainWindowAlsoClosesTheChannelListsBorrowedSelectionMenu(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture); fixture.window.show();
                var channels = (javafx.scene.control.ListView<?>) fixture.stage.getScene().lookup("#channels");
                channels.getOnContextMenuRequested().handle(mock(javafx.scene.input.ContextMenuEvent.class));
                var menu = (com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu) channels.getContextMenu().getItems().stream()
                        .filter(com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu.class::isInstance).findFirst().orElseThrow();
                assertThat(menu.getItems()).hasSize(2);
                fixture.window.close(); menu.refreshItems();
                assertThat(menu.getItems()).isEmpty(); assertThat(menu.getOnShowing()).isNull();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void closingTheMainWindowDetachesItsOwnControlsFromTheBorrowedModel(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture); fixture.window.show();
                var scene = fixture.stage.getScene();
                var name = (javafx.scene.control.Label) scene.lookup("#userNameLabel");
                var footer = (javafx.scene.control.Label) scene.lookup("#footerLabel");
                var clips = (javafx.scene.control.Button) scene.lookup("#clipButton");
                var search = (javafx.scene.control.Button) scene.lookup("#searchChannelButton");
                var followers = (javafx.scene.layout.Pane) scene.lookup("#followersContainer");
                var userMenu = (javafx.scene.control.MenuButton) scene.lookup("#userMenuButton");
                var auth = (com.github.k7t3.tcv.view.main.AuthenticationMenuItem) userMenu.getItems().getLast();
                fixture.window.close();
                assertThat(name.textProperty().isBound()).isFalse();
                assertThat(footer.textProperty().isBound()).isFalse();
                assertThat(clips.visibleProperty().isBound()).isFalse();
                assertThat(clips.disableProperty().isBound()).isFalse();
                assertThat(search.disableProperty().isBound()).isFalse();
                assertThat(search.getOnAction()).isNull();
                assertThat(followers.visibleProperty().isBound()).isFalse();
                assertThat(followers.managedProperty().isBound()).isFalse();
                assertThat(followers.disableProperty().isBound()).isFalse();
                assertThat(auth.authorizedProperty().isBound()).isFalse();
                assertThat(auth.getOnAction()).isNull();
                assertThat(fixture.model.isClosed()).isFalse();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void aClosedMainViewDoesNotHandleTheBorrowedModelsLateLoginNotification(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(true); reference.set(fixture); fixture.window.show();
                @SuppressWarnings("unchecked")
                var captured = (org.mockito.ArgumentCaptor<com.github.k7t3.tcv.app.model.TypeSafeNotificationObserver<com.github.k7t3.tcv.app.event.LoginEvent>>) (Object)
                        org.mockito.ArgumentCaptor.forClass(com.github.k7t3.tcv.app.model.TypeSafeNotificationObserver.class);
                verify(fixture.model, times(2)).subscribe(eq(com.github.k7t3.tcv.app.event.LoginEvent.class), captured.capture());
                var modal = (atlantafx.base.controls.ModalPane) fixture.stage.getScene().lookup("#modalPane");
                modal.show(new javafx.scene.layout.StackPane());
                captured.getValue().receivedNotification(new com.github.k7t3.tcv.app.event.LoginEvent());
                assertThat(modal.isDisplay()).isFalse();
                fixture.window.close();
                var later = new javafx.scene.layout.StackPane(); modal.show(later);
                captured.getValue().receivedNotification(new com.github.k7t3.tcv.app.event.LoginEvent());
                assertThat(modal.isDisplay()).isTrue();
                assertThat(modal.getContent()).isSameAs(later);
                modal.hide(true);
            });
        } finally { close(reference.get(), robot); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @SuppressWarnings({"rawtypes", "unchecked"})
    void aClosedMainViewUnregistersTheExactBorrowedModelObserver(boolean login, FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(true); reference.set(fixture); fixture.window.show();
                Class type = login ? com.github.k7t3.tcv.app.event.LoginEvent.class : com.github.k7t3.tcv.app.event.ClipPostedAppEvent.class;
                var observer = org.mockito.ArgumentCaptor.forClass(com.github.k7t3.tcv.app.model.TypeSafeNotificationObserver.class);
                verify(fixture.model, times(login ? 2 : 1)).subscribe(eq(type), observer.capture());
                clearInvocations(fixture.model);
                fixture.window.close(); fixture.window.close();
                for (var registered : observer.getAllValues())
                    verify(fixture.model, times(1)).unsubscribe(eq(type), same(registered));
                assertThat(fixture.model.isClosed()).isFalse();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void closingTheMainViewStopsItsClipAnimationAndRejectsLaterClipNotifications(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture); fixture.window.show();
                var button = (javafx.scene.control.Button) fixture.stage.getScene().lookup("#clipButton");
                var animation = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(30)));
                try (var effects = mockStatic(atlantafx.base.util.Animations.class, CALLS_REAL_METHODS)) {
                    effects.when(() -> atlantafx.base.util.Animations.wobble(button)).thenReturn(animation);
                    effects.clearInvocations();
                    fixture.model.publish(new com.github.k7t3.tcv.app.event.ClipPostedAppEvent());
                    assertThat(animation.getStatus()).isEqualTo(javafx.animation.Animation.Status.RUNNING);
                    fixture.window.close();
                    assertThat(animation.getStatus()).isEqualTo(javafx.animation.Animation.Status.STOPPED);
                    fixture.model.publish(new com.github.k7t3.tcv.app.event.ClipPostedAppEvent());
                    effects.verify(() -> atlantafx.base.util.Animations.wobble(button), times(1));
                    assertThat(animation.getStatus()).isEqualTo(javafx.animation.Animation.Status.STOPPED);
                } finally { animation.stop(); }
            });
        } finally { close(reference.get(), robot); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"groupCallerButton", "clipButton"})
    void mainWindowCloseReleasesTheGroupOrClipModalWithoutClosingBorrowedModels(String buttonId, FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(true); reference.set(fixture);
                var clips = fixture.model.getClipRepository();
                var source = new com.github.k7t3.tcv.support.ClipEventSource(clips,
                        mock(com.github.k7t3.tcv.domain.Twitch.class, RETURNS_DEEP_STUBS),
                        new com.github.k7t3.tcv.domain.channel.Broadcaster("123", "owner", "Owner", "https://images.invalid/owner.png", ""));
                source.post("clip", null);
                fixture.window.show();
                ((javafx.scene.control.Button) fixture.stage.getScene().lookup("#" + buttonId)).fire();
                var modal = (atlantafx.base.controls.ModalPane) fixture.stage.getScene().lookup("#modalPane");
                assertThat(modal.isDisplay()).isTrue();
                var content = modal.getContent();
                javafx.collections.ObservableList<?> projection = buttonId.equals("clipButton")
                        ? ((javafx.scene.control.ListView<?>) content.lookup("#videoClips")).getItems() : null;
                fixture.window.close();
                assertThat(modal.isDisplay()).isFalse(); assertThat(modal.getContent()).isNull();
                assertThat(clips.isClosed()).isFalse(); assertThat(clips.getItems()).containsKey("clip");
                if (projection != null) assertThat(projection).isEmpty();
                else assertThat(((javafx.scene.layout.Region) content).maxWidthProperty().isBound()).isFalse();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void closingTheMainWindowAlsoReleasesTheOwnedAboutWindow(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                fixture.window.show();
                var menu = (javafx.scene.control.MenuButton) fixture.stage.getScene().lookup("#userMenuButton");
                menu.getItems().stream().filter(item -> "aboutMenuItem".equals(item.getId())).findFirst().orElseThrow().fire();
                var child = javafx.stage.Window.getWindows().stream().filter(window -> window instanceof Stage)
                        .map(window -> (Stage) window).filter(window -> window.getOwner() == fixture.stage).findFirst().orElseThrow();
                assertThat(child.getScene().lookup("#okButton")).isNotNull();
                assertThat(child.isShowing()).isTrue();
                fixture.window.close();
                assertThat(child.isShowing()).isFalse(); assertThat(child.getScene()).isNull();
                assertThat(fixture.model.isClosed()).isFalse();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void closingTheWindowDetachesNotificationHistoryWhileItsBorrowedModelRemainsOpen(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture); fixture.window.show();
                var link = (javafx.scene.control.Hyperlink) fixture.stage.getScene().lookup("#liveStateLink");
                var channel = mock(com.github.k7t3.tcv.app.channel.TwitchChannelViewModel.class);
                when(channel.getUserName()).thenReturn("Channel");
                var record = new LiveStateNotificator.LiveStateRecord(channel, java.time.LocalTime.NOON, true);
                var notification = new com.github.k7t3.tcv.app.event.LiveNotificationEvent(record);
                var notifier = fixture.model.getLiveNotificator(); notifier.publish(notification);
                assertThat(link.getText()).contains("Channel");
                link.fire();
                var popup = javafx.stage.Window.getWindows().stream().filter(com.github.k7t3.tcv.view.core.BasicPopup.class::isInstance)
                        .map(com.github.k7t3.tcv.view.core.BasicPopup.class::cast).findFirst().orElseThrow();
                var history = (com.github.k7t3.tcv.view.channel.LiveStateNotificatorView) popup.getContent();
                fixture.window.close();
                when(channel.getUserName()).thenReturn("Later"); notifier.publish(notification);
                assertThat(link.getText()).doesNotContain("Later");
                assertThat(link.getOnAction()).isNull();
                assertThat(history.getItems()).isEmpty();
                assertThat(popup.isShowing()).isFalse(); assertThat(popup.getContent()).isNull();
                assertThat(notifier.isClosed()).isFalse();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void closingTheWindowAlsoDetachesItsChatContainerControls(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture); fixture.window.show();
                var loading = (javafx.scene.layout.VBox) fixture.stage.getScene().lookup("#loadStatePane");
                var selection = (javafx.scene.layout.HBox) fixture.stage.getScene().lookup("#selectingPane");
                var retry = (javafx.scene.control.Button) fixture.stage.getScene().lookup("#retryLoadButton");
                assertThat(loading.visibleProperty().isBound()).isTrue(); assertThat(selection.visibleProperty().isBound()).isTrue();
                fixture.window.close();
                assertThat(loading.visibleProperty().isBound()).isFalse(); assertThat(selection.visibleProperty().isBound()).isFalse();
                assertThat(retry.getOnAction()).isNull(); assertThat(fixture.model.getChatContainer().isClosed()).isFalse();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void malformedDisplayPreferencesStillAllowOpeningAndCancellingTheRealEditor(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                var node = fixture.preferences.getPreferences();
                node.put("theme", "NOT_A_THEME");
                node.put("theme.type", "NOT_A_SCHEME");
                node.putByteArray("chat.font.family", new byte[] {0, 1});
                node.put("chat.cache.size", "0");
                fixture.model.getChatContainer().bindChatPreferences(fixture.preferences.getChatPreferences());
                fixture.window.show();
                var menu = (javafx.scene.control.MenuButton) fixture.stage.getScene().lookup("#userMenuButton");
                menu.getItems().stream().filter(item -> "prefsMenuItem".equals(item.getId())).findFirst().orElseThrow().fire();
                var child = javafx.stage.Window.getWindows().stream().filter(window -> window instanceof Stage)
                        .map(window -> (Stage) window).filter(window -> window.getOwner() == fixture.stage).findFirst().orElseThrow();
                assertThat(((javafx.scene.control.ChoiceBox<?>) child.getScene().lookup("#themeChoiceBox")).getValue())
                        .isEqualTo(ThemeManager.DEFAULT_THEME);
                @SuppressWarnings("unchecked") var pages = (javafx.scene.control.TreeView<Object>) child.getScene().lookup("#pagesTreeView");
                pages.getSelectionModel().select(pages.getRoot().getChildren().get(1));
                assertThat(((javafx.scene.control.ComboBox<?>) child.getScene().lookup("#chatCacheSizeComboBox")).getValue()).isEqualTo(256);
                assertThat(((javafx.scene.control.ComboBox<?>) child.getScene().lookup("#fontSizeComboBox")).getValue())
                        .isEqualTo(javafx.scene.text.Font.getDefault().getSize());
                ((javafx.scene.control.Button) child.getScene().lookup("#cancelButton")).fire();
                assertThat(child.isShowing()).isFalse();
                assertThat(node.get("theme", null)).isEqualTo("NOT_A_THEME");
                assertThat(node.get("theme.type", null)).isEqualTo("NOT_A_SCHEME");
                assertThat(node.getByteArray("chat.font.family", null)).containsExactly(0, 1);
                assertThat(node.get("chat.cache.size", null)).isEqualTo("0");
                assertThat(fixture.failures).isEmpty();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void closingTheMainWindowReleasesItsOwnedPreferencesWindow(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                fixture.window.show();
                var menu = (javafx.scene.control.MenuButton) fixture.stage.getScene().lookup("#userMenuButton");
                var preferences = menu.getItems().stream().filter(item -> "prefsMenuItem".equals(item.getId())).findFirst().orElseThrow();
                preferences.fire();
                var child = javafx.stage.Window.getWindows().stream().filter(window -> window instanceof Stage)
                        .map(window -> (Stage) window).filter(window -> window.getOwner() == fixture.stage).findFirst().orElseThrow();
                assertThat(child.isShowing()).isTrue();
                assertThat(child.getScene().getRoot().getId()).isEqualTo("prefsRootStackPane");
                fixture.window.close();
                assertThat(child.isShowing()).isFalse();
                assertThat(child.getScene()).isNull();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void closingTheMainWindowAlsoClosesTheSearchModalWhileItsParentStillExists(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(true); reference.set(fixture);
                fixture.window.show();
                var search = (javafx.scene.control.Button) fixture.stage.getScene().lookup("#searchChannelButton");
                assertThat(search.isDisabled()).isFalse();
                search.fire();
                var modal = (atlantafx.base.controls.ModalPane) fixture.stage.getScene().lookup("#modalPane");
                var content = (javafx.scene.layout.Pane) modal.getContent();
                assertThat(content).isNotNull();
                assertThat(content.prefWidthProperty().isBound()).isTrue();
                fixture.window.close();
                assertThat(modal.getContent()).isNull();
                assertThat(content.prefWidthProperty().isBound()).isFalse();
                assertThat(content.prefHeightProperty().isBound()).isFalse();
            });
        } finally { close(reference.get(), robot); }
    }
    @TempDir Path directory;

    @Test
    void anExplicitShutdownAlsoSavesBoundsOnceBeforeDetachingTheStage(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                fixture.window.show();
                fixture.stage.setX(170);
                fixture.window.close();
                fixture.window.close();
                assertThat(fixture.queue).hasSize(1);
            });
            var fixture = reference.get();
            fixture.queue.remove().run();
            assertThat(fixture.helper.getWindowBoundsService().getBounds("main").x()).isEqualTo(170);
        } finally { close(reference.get(), robot); }
    }

    @Test
    void closingDuringFxmlInitializationStopsTheRemainingWindowSetup(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                doAnswer(invocation -> {
                    invocation.callRealMethod();
                    fixture.window.close();
                    return null;
                }).when(fixture.controller).initialize(any(), any());

                fixture.window.show();

                assertThat(fixture.stage.isShowing()).isFalse();
                assertThat(fixture.stage.getScene()).isNull();
                assertThat(fixture.stage.titleProperty().isBound()).isFalse();
                assertThat(fixture.helper.getPrimaryStage()).isNull();
                assertThat(fixture.model.isClosed()).isFalse();
                verify(fixture.controller).close();
                verify(fixture.controller, never()).startMainView();
            });
        } finally { close(reference.get(), robot); }
    }

    @ParameterizedTest
    @ValueSource(strings = {"theme", "theme-type", "title", "icons", "bounds", "scene"})
    void closingFromASetupNotificationCannotReopenTheStage(String boundary, FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(new com.github.k7t3.tcv.view.core.WindowBounds(30, 40, 910, 620, false));
                reference.set(fixture);
                var requested = new AtomicInteger();
                Runnable shutdown = () -> { requested.incrementAndGet(); fixture.window.close(); };
                switch (boundary) {
                    case "theme" -> fixture.theme.themeProperty().addListener((observable, previous, current) -> shutdown.run());
                    case "theme-type" -> fixture.theme.themeTypeProperty().addListener((observable, previous, current) -> shutdown.run());
                    case "title" -> fixture.stage.titleProperty().addListener((observable, previous, current) -> shutdown.run());
                    case "icons" -> fixture.stage.getIcons().addListener((javafx.collections.ListChangeListener<javafx.scene.image.Image>) change -> shutdown.run());
                    case "bounds" -> fixture.stage.xProperty().addListener((observable, previous, current) -> shutdown.run());
                    case "scene" -> fixture.stage.sceneProperty().addListener((observable, previous, current) -> {
                        if (current != null) shutdown.run();
                    });
                    default -> throw new AssertionError(boundary);
                }

                fixture.window.show();

                assertThat(requested).hasValue(1);
                assertThat(fixture.stage.isShowing()).isFalse();
                assertThat(fixture.stage.getScene()).isNull();
                assertThat(fixture.stage.titleProperty().isBound()).isFalse();
                assertThat(fixture.helper.getPrimaryStage()).isNull();
                assertThat(fixture.model.isClosed()).isFalse();
                if (boundary.equals("bounds")) assertThat(fixture.stage.getY()).isNaN();
                verify(fixture.controller).close();
                verify(fixture.controller, never()).startMainView();
            });
        } finally {
            robot.interact(() -> {
                var fixture = reference.get();
                if (fixture == null) return;
                fixture.stage.hide();
                fixture.stage.setScene(null);
                fixture.stage.titleProperty().unbind();
            });
            close(reference.get(), robot);
        }
    }

    @Test
    void aRealFxmlWindowReleasesBindingsStageAndOwnedUiWithoutClosingBorrowedModels(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                fixture.window.show();
                assertThat(fixture.stage.isShowing()).isTrue();
                assertThat(fixture.stage.titleProperty().isBound()).isTrue();
                fixture.window.close();
                fixture.window.close();
                assertThat(fixture.stage.isShowing()).isFalse();
                assertThat(fixture.stage.getScene()).isNull();
                assertThat(fixture.stage.titleProperty().isBound()).isFalse();
                assertThat(fixture.helper.getPrimaryStage()).isNull();
                assertThat(fixture.model.isClosed()).isFalse();
                fixture.preferences.getStatePreferences().setOnlyLive(true);
                assertThat(fixture.model.getChannelListViewModel().isOnlyLive()).isFalse();
                verify(fixture.controller).close();
                assertThat(fixture.hidden).hasValue(0);
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void failureAfterThemeAndTitleInstallationCanStillReleaseThePartiallyLoadedWindow(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                var failure = new IllegalStateException("window bounds failed");
                doThrow(failure).when(fixture.helper).getWindowBoundsService();
                assertThatThrownBy(fixture.window::show).isSameAs(failure);
                fixture.window.close();
                assertThat(fixture.stage.titleProperty().isBound()).isFalse();
                assertThat(fixture.stage.getScene()).isNull();
                assertThatThrownBy(() -> fixture.theme.setScene(new javafx.scene.Scene(new javafx.scene.layout.Pane())))
                        .isInstanceOf(IllegalStateException.class);
                verify(fixture.controller).close();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void fxmlInitializationFailureDoesNotRequireACompletedViewTupleToReleaseTheController(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                var failure = new IllegalStateException("FXML initialization failed");
                doThrow(failure).when(fixture.controller).initialize(any(), any());
                assertThatThrownBy(fixture.window::show).hasStackTraceContaining("FXML initialization failed");
                fixture.window.close();
                verify(fixture.controller).close();
                assertThat(fixture.stage.getScene()).isNull();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void aShownCallbackFailureReachesTheStartupOwner(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                var failure = new IllegalStateException("authentication view failed");
                doThrow(failure).when(fixture.controller).startMainView();
                fixture.window.show();
                assertThat(fixture.failures).containsExactly(failure);
                verify(fixture.controller).startMainView();
            });
        } finally { close(reference.get(), robot); }
    }

    @Test
    void hidingQueuesWindowBoundsOnTheOwnedWorkerBeforeDatabaseRelease(FxRobot robot) {
        var reference = new AtomicReference<Fixture>();
        try {
            robot.interact(() -> {
                var fixture = new Fixture(); reference.set(fixture);
                fixture.window.show();
                fixture.stage.setX(140);
                fixture.stage.hide();
                assertThat(fixture.hidden).hasValue(1);
                assertThat(fixture.queue).hasSize(1);
                fixture.window.close();
                fixture.model.close();
            });
            var fixture = reference.get();
            fixture.queue.remove().run();
            assertThat(fixture.helper.getWindowBoundsService().getBounds("main").x()).isEqualTo(140);
        } finally { close(reference.get(), robot); }
    }

    private void close(Fixture fixture, FxRobot robot) {
        if (fixture == null) return;
        robot.interact(() -> {
            fixture.window.close();
            fixture.model.close();
            fixture.helper.prepareShutdown();
            Application.setUserAgentStylesheet(fixture.previousStyle);
        });
        Runnable pending;
        while ((pending = fixture.queue.poll()) != null) pending.run();
        fixture.helper.close();
    }

    private final class Fixture {
        final String previousStyle = Application.getUserAgentStylesheet();
        final TaskWorker worker = mock(TaskWorker.class);
        final LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
        final AppHelper helper;
        final AppPreferences preferences = new AppPreferences(new MemoryPreferences());
        final MainViewModel model;
        final MainView controller;
        final ThemeManager theme = new ThemeManager();
        final Stage stage = new Stage();
        final AtomicInteger hidden = new AtomicInteger();
        final ArrayList<Throwable> failures = new ArrayList<>();
        final MainWindow window;
        Fixture() { this(false); }
        Fixture(com.github.k7t3.tcv.view.core.WindowBounds savedBounds) {
            this(false, false, f -> MainViewFactory.create(f.stage, f.model, f.helper, f.preferences, f.theme,
                    mock(com.github.k7t3.tcv.domain.auth.CredentialStore.class)), savedBounds);
        }
        Fixture(boolean authorized) { this(authorized, authorized); }
        Fixture(boolean authorized, boolean observe) {
            this(authorized, observe, f -> MainViewFactory.create(f.stage, f.model, f.helper, f.preferences, f.theme,
                    mock(com.github.k7t3.tcv.domain.auth.CredentialStore.class)));
        }
        Fixture(boolean authorized, boolean observe, java.util.function.Function<Fixture, MainView> controllers) {
            this(authorized, observe, controllers, null);
        }
        Fixture(boolean authorized, boolean observe, java.util.function.Function<Fixture, MainView> controllers,
                com.github.k7t3.tcv.view.core.WindowBounds savedBounds) {
            when(worker.getExecutor()).thenReturn(queue::add);
            helper = spy(new AppHelper(worker, new EventPublishers(), unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); }));
            var file = new UserDataFile(directory.resolve("userdata.db"));
            try { file.connectDatabase(DatabaseVersion.EMPTY); }
            catch (IOException failure) { throw new UncheckedIOException(failure); }
            helper.setUserDataFile(file);
            helper.setPrimaryStage(stage);
            var main = MainModelFactory.create(helper, helper.getChannelGroupRepository(), helper.getImageLoader(), failures::add);
            model = observe ? spy(main) : main;
            // 検索ボタンの表示許可だけを与え、実際のログイン・通信は開始しない。
            if (authorized) doReturn(new javafx.beans.property.ReadOnlyBooleanWrapper(true).getReadOnlyProperty())
                    .when(model).authorizedProperty();
            controller = spy(controllers.apply(this));
            doNothing().when(controller).startMainView();
            window = new MainWindow(stage, model, helper, preferences,
                    savedBounds == null ? helper.getWindowBoundsService().getBounds("main") : savedBounds,
                    hidden::incrementAndGet, failures::add, controller, theme);
        }
    }
}
