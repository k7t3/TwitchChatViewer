package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.core.FloatableStage;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatContainerOwnershipTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aChildReturnedAfterReentrantParentCloseIsReleasedWithoutBeingShown(boolean floating, FxRobot robot) {
        robot.interact(() -> {
            var owner = new AtomicReference<Fixture>(); var released = new AtomicInteger();
            var node = new StackPane(); var window = floating ? new FloatableStage() : null;
            try (var f = new Fixture((chat, floated) -> {
                owner.get().view.close();
                return new ChatContainerView.LoadedChat(node, () -> { released.incrementAndGet(); if (window != null) { window.close(); window.setScene(null); } }, window);
            })) {
                owner.set(f); var chat = mock(ChatRoomViewModel.class);
                (floating ? f.model.getFloatingChatRoomList() : f.model.getChatRoomList()).add(chat);
                assertThat(released).hasValue(1); assertThat(node.getParent()).isNull();
                if (window != null) assertThat(window.isShowing()).isFalse();
                f.view.close(); assertThat(released).hasValue(1);
            } finally { if (window != null) { window.close(); window.setScene(null); } }
        });
    }

    @Test
    void removingAndReaddingTheSameChatBuildsANewOwnedViewAndDetachesTheOldDragHandlers(FxRobot robot) {
        robot.interact(() -> {
            var loaded = new ArrayList<StackPane>(); var released = new AtomicInteger();
            try (var f = new Fixture((chat, floating) -> {
                var node = new StackPane(); loaded.add(node);
                return new ChatContainerView.LoadedChat(node, released::incrementAndGet, null);
            })) {
                var chat = mock(ChatRoomViewModel.class); f.model.getChatRoomList().add(chat);
                var first = loaded.getFirst(); assertThat(first.getParent()).isNotNull(); assertThat(first.getOnDragDetected()).isNotNull();
                f.model.getChatRoomList().remove(chat);
                assertThat(first.getParent()).isNull(); assertThat(first.getOnDragDetected()).isNull(); assertThat(released).hasValue(1);
                f.model.getChatRoomList().add(chat); assertThat(loaded).hasSize(2); assertThat(loaded.getLast().getParent()).isNotNull();
                f.view.close(); assertThat(released).hasValue(2); assertThat(loaded.getLast().getParent()).isNull();
            }
        });
    }

    @Test
    void droppingAChatOnAnotherSwapsTheirPositionsWhenTheDragboardPayloadIsUnreadable(FxRobot robot) {
        robot.interact(() -> {
            var chats = new ArrayList<StackPane>();
            try (var f = new Fixture((chat, floating) -> {
                var node = new StackPane();
                chats.add(node);
                return new ChatContainerView.LoadedChat(node, () -> {}, null);
            })) {
                f.model.getChatRoomList().addAll(mock(ChatRoomViewModel.class), mock(ChatRoomViewModel.class));
                var source = chats.getFirst();
                var target = chats.getLast();
                var dragboard = mock(Dragboard.class);
                // JavaFX 27 はシリアライズされた Integer を復元せず、バイト列のまま返す。
                var chatFormat = DataFormat.lookupMimeType("application/chat-view");
                when(dragboard.hasContent(chatFormat)).thenReturn(true);
                when(dragboard.getContent(chatFormat)).thenReturn(ByteBuffer.allocate(0));
                when(dragboard.getTransferModes()).thenReturn(Set.of(TransferMode.MOVE));

                var over = new DragEvent(DragEvent.DRAG_OVER, dragboard, 0, 0, 0, 0,
                        TransferMode.MOVE, source, null, null);
                target.fireEvent(over);
                assertThat(over.getAcceptedTransferMode()).isEqualTo(TransferMode.MOVE);
                var drop = new DragEvent(DragEvent.DRAG_DROPPED, dragboard, 0, 0, 0, 0,
                        TransferMode.MOVE, source, target, null);
                target.fireEvent(drop);

                assertThat(drop.isDropCompleted()).isTrue();
                assertThat(chats).extracting(GridPane::getColumnIndex).containsExactly(1, 0);
            }
        });
    }

    @Test
    void droppingAChatRestoresTheHighlightedViewsOriginalOpacity(FxRobot robot) throws InterruptedException {
        var sourceRoom = mock(ChatRoomViewModel.class);
        var targetRoom = mock(ChatRoomViewModel.class);
        var source = new StackPane();
        var target = new StackPane();
        var originalOpacity = 0.85;
        var flashing = new CountDownLatch(1);
        var dragboard = mock(Dragboard.class);
        var f = WaitForAsyncUtils.waitForAsyncFx(5_000, () -> new Fixture((chat, floating) ->
                new ChatContainerView.LoadedChat(chat == sourceRoom ? source : target, () -> {}, null)));
        try {
            robot.interact(() -> {
                f.model.getChatRoomList().addAll(sourceRoom, targetRoom);
                target.setOpacity(originalOpacity);
                target.opacityProperty().addListener((_, _, opacity) -> {
                    if (opacity.doubleValue() < 0.5) flashing.countDown();
                });
                when(dragboard.hasContent(DataFormat.lookupMimeType("application/chat-view"))).thenReturn(true);
                target.fireEvent(new DragEvent(DragEvent.DRAG_ENTERED, dragboard, 0, 0, 0, 0,
                        TransferMode.MOVE, source, null, null));
            });
            assertThat(flashing.await(5, TimeUnit.SECONDS)).as("the target flashed before the drop").isTrue();

            robot.interact(() -> {
                target.fireEvent(new DragEvent(DragEvent.DRAG_DROPPED, dragboard, 0, 0, 0, 0,
                        TransferMode.MOVE, source, target, null));

                assertThat(target.getOpacity()).isEqualTo(originalOpacity);
            });
        } finally {
            robot.interact(f::close);
        }
    }

    @Test
    void closingAttemptsEveryChildEvenWhenOneChildCannotRelease(FxRobot robot) {
        robot.interact(() -> {
            var released = new ArrayList<ChatRoomViewModel>(); var first = mock(ChatRoomViewModel.class); var second = mock(ChatRoomViewModel.class);
            var failure = new IllegalStateException("chat view release failed");
            try (var f = new Fixture((chat, floating) -> new ChatContainerView.LoadedChat(new StackPane(), () -> {
                released.add(chat); if (chat == first) throw failure;
            }, null))) {
                f.model.getChatRoomList().addAll(first, second);
                assertThatThrownBy(f.view::close).isSameAs(failure);
                assertThat(released).containsExactlyInAnyOrder(first, second);
                f.model.getChatRoomList().clear(); f.model.getChatRoomList().add(first);
                assertThat(released).containsExactlyInAnyOrder(first, second); verify(f.model, never()).close();
            }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void parentCloseAndListRemovalBothCloseFloatingWindows(boolean parentClose, FxRobot robot) {
        robot.interact(() -> {
            var released = new AtomicInteger(); var child = new FloatableStage();
            try (var f = new Fixture((chat, floating) -> new ChatContainerView.LoadedChat(new StackPane(), () -> {
                released.incrementAndGet(); child.close(); child.setScene(null);
            }, child))) {
                var chat = mock(ChatRoomViewModel.class); f.model.getFloatingChatRoomList().add(chat);
                assertThat(child.isShowing()).isTrue();
                if (parentClose) f.view.close(); else f.model.getFloatingChatRoomList().remove(chat);
                assertThat(child.isShowing()).isFalse(); assertThat(child.getScene()).isNull(); assertThat(released).hasValue(1);
                verify(chat, never()).leaveChatAsync();
            } finally { child.close(); child.setScene(null); }
        });
    }

    @Test
    void aChildFxmlInitializationFailureReleasesItsControllerBeforeReturningToTheParent(FxRobot robot) {
        robot.interact(() -> {
            var controller = spy(new SingleChatRoomView(mock(com.github.k7t3.tcv.app.group.ChannelGroupRepository.class)));
            var failure = new IllegalStateException("partial child FXML failed");
            doThrow(failure).when(controller).initialize(any(), any());
            var errors = new ArrayList<Throwable>(); var thread = Thread.currentThread(); var previous = thread.getUncaughtExceptionHandler();
            try (var f = new Fixture((room, floating, owner) -> ChatContainerView.load(SingleChatRoomView.class, controller,
                    (com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel) room, owner))) {
                thread.setUncaughtExceptionHandler((source, error) -> errors.add(error));
                var room = mock(com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel.class); f.model.getChatRoomList().add(room);
                assertThat(errors).hasSize(1); var original = errors.getFirst(); while (original.getCause() != null) original = original.getCause();
                assertThat(original).isSameAs(failure); verify(controller).close();
                assertThat(f.root.lookup("#scrollToEnd")).isNull(); verify(room, never()).joinChatAsync();
                f.view.close(); verify(controller, times(1)).close();
            } finally { thread.setUncaughtExceptionHandler(previous); }
        });
    }

    private static final class Fixture implements AutoCloseable {
        final ChatRoomContainerViewModel model = mock(ChatRoomContainerViewModel.class);
        final ChatContainerView view;
        final Parent root;
        final Stage window = new Stage();
        Fixture(BiFunction<ChatRoomViewModel, Boolean, ChatContainerView.LoadedChat> loader) {
            this((room, floating, owner) -> loader.apply(room, floating));
        }
        Fixture(ChatContainerView.Loader loader) {
            when(model.getChatRoomList()).thenReturn(FXCollections.observableArrayList());
            when(model.getFloatingChatRoomList()).thenReturn(FXCollections.observableArrayList());
            when(model.loadingProperty()).thenReturn(new ReadOnlyBooleanWrapper().getReadOnlyProperty());
            when(model.errorProperty()).thenReturn(new ReadOnlyStringWrapper("").getReadOnlyProperty());
            when(model.selectingCountProperty()).thenReturn(new ReadOnlyIntegerWrapper().getReadOnlyProperty());
            when(model.selectModeProperty()).thenReturn(new ReadOnlyBooleanWrapper().getReadOnlyProperty());
            view = new ChatContainerView(loader);
            root = FluentViewLoader.fxmlView(ChatContainerView.class).codeBehind(view).viewModel(model).resourceBundle(Resources.getResourceBundle()).load().getView();
            window.setScene(new Scene(new StackPane(root), 400, 280)); window.show(); root.applyCss(); root.layout();
        }
        @Override public void close() { view.close(); window.close(); window.setScene(null); }
    }
}
