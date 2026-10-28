package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.chat.ChatEmoteStore;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.domain.chat.ChatMessageFragment;
import com.github.k7t3.tcv.view.image.LazyImageView;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.application.Platform;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatDataCellLifetimeTest {
    @ParameterizedTest @ValueSource(strings = {"normal", "merged", "system", "subscription", "cheer", "hidden", "deleted"})
    void disposingACellReleasesItsTextAndImageBindingsWithoutClosingBorrowedImages(String kind, FxRobot robot) {
        robot.interact(() -> {
            var f = new Fixture(kind);
            var nodes = new ArrayList<>(f.cell.getChildren());
            var images = images(nodes); assertThat(images).isNotEmpty();
            var text = nodes.stream().filter(Text.class::isInstance).map(Text.class::cast).toList();
            assertThat(text).isNotEmpty(); assertThat(text.stream().anyMatch(node -> node.fontProperty().isBound())).isTrue();
            f.cell.dispose(); f.cell.dispose();
            assertThat(f.cell.getChildren()).isEmpty(); assertThat(f.cell.getOnContextMenuRequested()).isNull();
            for (var node : text) {
                assertThat(node.fontProperty().isBound()).isFalse(); assertThat(node.fillProperty().isBound()).isFalse();
                assertThat(node.visibleProperty().isBound()).isFalse(); assertThat(node.managedProperty().isBound()).isFalse();
            }
            for (var image : images) {
                assertThat(image.getLazyImage()).isNull(); assertThat(image.imageProperty().isBound()).isFalse();
                assertThat(image.fitWidthProperty().isBound()).isFalse(); assertThat(image.fitHeightProperty().isBound()).isFalse();
            }
            f.deleted.set(true); f.hidden.set(true); f.font.set(new ChatFont("System", 24)); f.image.set(new WritableImage(4, 4));
            assertThat(f.cell.getChildren()).isEmpty();
            for (var borrowed : f.borrowed) verify(borrowed, never()).close();
        });
    }

    @Test
    void aRemovedMessageReleasesItsImagesAndShowsOnlyOneReplacementWithDeletionTakingPrecedence(FxRobot robot) {
        robot.interact(() -> {
            var f = new Fixture("normal");
            try {
                var images = images(new ArrayList<>(f.cell.getChildren())); assertThat(images).hasSize(3);
                f.hidden.set(true); f.deleted.set(true); f.hidden.set(false); f.hidden.set(true);
                var replacement = f.cell.getChildren().stream().filter(Text.class::isInstance).map(Text.class::cast)
                        .filter(text -> text.getStyleClass().contains("chat-text")).map(Text::getText).toList();
                assertThat(replacement).containsExactly(Resources.getString("chat.message.deleted"));
                // The header badge remains; the removed emote and emoji no longer observe their borrowed images.
                for (var image : images.subList(1, images.size())) {
                    assertThat(image.imageProperty().isBound()).isFalse(); assertThat(image.getLazyImage()).isNull();
                }
            } finally { f.cell.dispose(); }
        });
    }

    @Test
    void disposingTheCellAlsoRejectsALateAcceptedAnswerFromItsMenu(FxRobot robot) {
        robot.interact(() -> {
            var dialog = mock(TextInputDialog.class); var reference = new AtomicReference<Fixture>();
            when(dialog.showAndWait()).thenAnswer(ignored -> { reference.get().cell.dispose(); return Optional.of("late"); });
            var f = new Fixture("normal", model -> new ChatDataContextMenu(model, () -> dialog)); reference.set(f);
            var stage = new Stage(); stage.setScene(new Scene(new StackPane(f.cell), 400, 200)); stage.show();
            ChatDataContextMenu menu = null;
            try {
                f.cell.fireEvent(contextRequest()); menu = showingMenu();
                menu.getItems().stream().filter(item -> Resources.getString("chat.menu.filter.user").equals(item.getText())).findFirst().orElseThrow().fire();
                verify(f.model, never()).userFilter(anyString()); verify(dialog).close();
                assertThat(menu.getItems()).isEmpty(); assertThat(menu.isShowing()).isFalse();
            } finally { f.cell.dispose(); if (menu != null) menu.close(); stage.close(); stage.setScene(null); }
        });
    }

    @Test
    void aMenuReturnedAfterItsCellEndedIsClosedWithoutShowing(FxRobot robot) {
        robot.interact(() -> {
            var reference = new AtomicReference<Fixture>(); var returned = new AtomicReference<ChatDataContextMenu>();
            var f = new Fixture("normal", model -> {
                reference.get().cell.dispose(); var menu = new ChatDataContextMenu(model); returned.set(menu); return menu;
            }); reference.set(f);
            var stage = new Stage(); stage.setScene(new Scene(new StackPane(f.cell), 400, 200)); stage.show();
            try {
                f.cell.fireEvent(contextRequest());
                assertThat(returned.get().isShowing()).isFalse(); assertThat(returned.get().getItems()).isEmpty();
                assertThat(f.cell.getChildren()).isEmpty();
            } finally { f.cell.dispose(); if (returned.get() != null) returned.get().close(); stage.close(); stage.setScene(null); }
        });
    }

    @Test
    void disposingTheCellClosesAnActualModalConfirmationBeforeItsNestedLoopReturns(FxRobot robot) {
        robot.interact(() -> {
            var dialog = new TextInputDialog(); var reference = new AtomicReference<Fixture>(); var closedByCell = new AtomicBoolean();
            var f = new Fixture("normal", model -> new ChatDataContextMenu(model, () -> {
                Platform.runLater(() -> {
                    try { reference.get().cell.dispose(); closedByCell.set(!dialog.isShowing()); }
                    finally {
                        // Bound the regression even if cleanup fails before closing this modal.
                        dialog.setResult(null); dialog.close();
                    }
                });
                return dialog;
            })); reference.set(f);
            var stage = new Stage(); stage.setScene(new Scene(new StackPane(f.cell), 400, 200)); stage.show();
            ChatDataContextMenu menu = null;
            try {
                f.cell.fireEvent(contextRequest()); menu = showingMenu();
                menu.getItems().stream().filter(item -> Resources.getString("chat.menu.filter.user").equals(item.getText())).findFirst().orElseThrow().fire();
                assertThat(closedByCell).isTrue(); verify(f.model, never()).userFilter(anyString());
            } finally { f.cell.dispose(); if (menu != null) menu.close(); dialog.close(); stage.close(); stage.setScene(null); }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void hidingOrDeletingAMessageClosesItsMenuAndDisablesItsOldActions(boolean deleted, FxRobot robot) {
        robot.interact(() -> {
            var f = new Fixture("normal"); var stage = new Stage(); stage.setScene(new Scene(new StackPane(f.cell), 400, 200)); stage.show();
            ChatDataContextMenu menu = null;
            try {
                f.cell.fireEvent(contextRequest()); menu = showingMenu(); var copy = menu.getItems().getFirst();
                if (deleted) f.deleted.set(true); else f.hidden.set(true);
                assertThat(menu.isShowing()).isFalse(); assertThat(menu.getItems()).isEmpty();
                assertThat(f.cell.getOnContextMenuRequested()).isNull(); copy.fire(); verify(f.model, never()).copyMessage();
            } finally { f.cell.dispose(); if (menu != null) menu.close(); stage.close(); stage.setScene(null); }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void closingTheMessageViewOrRemovingItsMessageDisposesTheActualFlowlessCell(boolean removeMessage, FxRobot robot) {
        robot.interact(() -> {
            var f = new Fixture("normal"); f.cell.dispose();
            var model = mock(com.github.k7t3.tcv.app.chat.ChatRoomViewModel.class);
            var items = FXCollections.observableArrayList(f.model); when(model.getChatDataList()).thenReturn(items);
            when(model.autoScrollProperty()).thenReturn(new SimpleBooleanProperty(true));
            var root = new StackPane(); var messages = ChatRoomViewUtils.installMessages(root, model, false);
            var stage = new Stage(); stage.setScene(new Scene(root, 400, 200)); stage.show(); root.applyCss(); root.layout();
            try {
                var cell = root.lookupAll("*").stream().filter(ChatDataCell.class::isInstance).map(ChatDataCell.class::cast).findFirst().orElseThrow();
                assertThat(cell.getChildren()).isNotEmpty();
                if (removeMessage) { items.clear(); root.layout(); } else messages.unsubscribe();
                assertThat(cell.getChildren()).isEmpty(); f.deleted.set(true); f.hidden.set(true); assertThat(cell.getChildren()).isEmpty();
                for (var image : f.borrowed) verify(image, never()).close();
            } finally { messages.unsubscribe(); stage.close(); stage.setScene(null); }
        });
    }

    @Test
    void activeMessageImagesKeepTheirExistingSizesAndFollowTheFontScale(FxRobot robot) {
        robot.interact(() -> {
            var f = new Fixture("normal");
            try {
                var images = images(new ArrayList<>(f.cell.getChildren()));
                assertThat(images.stream().map(LazyImageView::getFitWidth).toList()).containsExactly(18.0, 24.0, 20.0);
                assertThat(images.stream().map(LazyImageView::getFitHeight).toList()).containsExactly(18.0, 24.0, 20.0);
                f.font.set(new ChatFont("System", ChatFont.getDefault().getSize() * 2));
                assertThat(images.stream().map(LazyImageView::getFitWidth).toList()).containsExactly(36.0, 48.0, 40.0);
                assertThat(images.stream().map(LazyImageView::getFitHeight).toList()).containsExactly(36.0, 48.0, 40.0);
            } finally { f.cell.dispose(); }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"subscriptionEmpty", "cheerEmpty"})
    void disposingAnEmptySubscriptionOrCheerAlsoReleasesItsPrefixFont(String kind, FxRobot robot) {
        robot.interact(() -> {
            var f = new Fixture(kind);
            var text = f.cell.getChildren().stream().filter(Text.class::isInstance).map(Text.class::cast).toList();
            assertThat(text).hasSize(1); assertThat(text.getFirst().fontProperty().isBound()).isTrue();
            f.cell.dispose(); assertThat(f.cell.getChildren()).isEmpty(); assertThat(text.getFirst().fontProperty().isBound()).isFalse();
        });
    }

    @Test
    void aConfirmationCleanupFailureCannotLeaveTheNowHiddenMessageVisible(FxRobot robot) {
        robot.interact(() -> {
            var dialog = mock(TextInputDialog.class); var failure = new IllegalStateException("confirmation cleanup failed");
            var reference = new AtomicReference<Fixture>(); var errors = new ArrayList<Throwable>();
            when(dialog.showAndWait()).thenAnswer(ignored -> { reference.get().hidden.set(true); return Optional.of("late"); });
            doThrow(failure).when(dialog).close();
            var f = new Fixture("normal", model -> new ChatDataContextMenu(model, () -> dialog)); reference.set(f);
            var stage = new Stage(); stage.setScene(new Scene(new StackPane(f.cell), 400, 200)); stage.show();
            var thread = Thread.currentThread(); var previous = thread.getUncaughtExceptionHandler(); ChatDataContextMenu menu = null;
            try {
                f.cell.fireEvent(contextRequest()); menu = showingMenu(); thread.setUncaughtExceptionHandler((source, error) -> errors.add(error));
                menu.getItems().stream().filter(item -> Resources.getString("chat.menu.filter.user").equals(item.getText())).findFirst().orElseThrow().fire();
                var replacement = f.cell.getChildren().stream().filter(Text.class::isInstance).map(Text.class::cast)
                        .filter(text -> text.getStyleClass().contains("chat-text")).map(Text::getText).toList();
                assertThat(replacement).containsExactly(Resources.getString("chat.message.hidden"));
                assertThat(errors).containsExactly(failure); verify(f.model, never()).userFilter(anyString());
            } finally { thread.setUncaughtExceptionHandler(previous); f.cell.dispose(); if (menu != null) menu.close(); stage.close(); stage.setScene(null); }
        });
    }

    @Test
    void disposingOneCellLeavesAnotherCellUsingTheSameModelAndImagesActive(FxRobot robot) {
        robot.interact(() -> {
            var f = new Fixture("normal"); var peer = ChatDataCell.of(f.model);
            try {
                f.cell.dispose(); var decoded = new WritableImage(5, 5); f.image.set(decoded);
                assertThat(images(new ArrayList<>(peer.getChildren())).stream().map(LazyImageView::getImage).toList())
                        .containsExactly(decoded, decoded, decoded);
                f.deleted.set(true);
                var replacement = peer.getChildren().stream().filter(Text.class::isInstance).map(Text.class::cast)
                        .filter(text -> text.getStyleClass().contains("chat-text")).map(Text::getText).toList();
                assertThat(replacement).containsExactly(Resources.getString("chat.message.deleted"));
                assertThat(f.cell.getChildren()).isEmpty();
                for (var image : f.borrowed) verify(image, never()).close();
            } finally { f.cell.dispose(); peer.dispose(); }
        });
    }

    private static ContextMenuEvent contextRequest() { return new ContextMenuEvent(ContextMenuEvent.CONTEXT_MENU_REQUESTED, 0, 0, 20, 20, false, null); }
    private static ChatDataContextMenu showingMenu() {
        return Window.getWindows().stream().filter(ChatDataContextMenu.class::isInstance).map(ChatDataContextMenu.class::cast).findFirst().orElseThrow();
    }

    private static List<LazyImageView> images(List<Node> nodes) {
        return nodes.stream().filter(Label.class::isInstance).map(Label.class::cast).map(Label::getGraphic)
                .filter(LazyImageView.class::isInstance).map(LazyImageView.class::cast).toList();
    }

    private static final class Fixture {
        final ChatDataViewModel model = mock(ChatDataViewModel.class);
        final SimpleBooleanProperty deleted = new SimpleBooleanProperty();
        final SimpleBooleanProperty hidden = new SimpleBooleanProperty();
        final SimpleObjectProperty<ChatFont> font = new SimpleObjectProperty<>(ChatFont.getDefault());
        final ReadOnlyObjectWrapper<Image> image = new ReadOnlyObjectWrapper<>(new WritableImage(2, 2));
        final List<LazyImage> borrowed = new ArrayList<>();
        final ChatDataCell cell;
        Fixture(String kind) { this(kind, ChatDataContextMenu::new); }
        Fixture(String kind, Function<ChatDataViewModel, ChatDataContextMenu> menus) {
            when(model.visibleBadgeProperty()).thenReturn(new SimpleBooleanProperty(true));
            when(model.visibleNameProperty()).thenReturn(new SimpleBooleanProperty(true));
            when(model.deletedProperty()).thenReturn(deleted); when(model.hiddenProperty()).thenReturn(hidden); when(model.fontProperty()).thenReturn(font);
            when(model.colorProperty()).thenReturn(new ReadOnlyObjectWrapper<>(Color.BLUE).getReadOnlyProperty());
            when(model.getUserName()).thenReturn("viewer"); when(model.getDisplayName()).thenReturn("Viewer");
            var empty = kind.endsWith("Empty");
            var message = new ChatMessage(empty ? "" : "hello :emote: smile", empty ? List.of() : List.of(ChatMessageFragment.text("hello"),
                    ChatMessageFragment.emote("emote", "Emote"), ChatMessageFragment.emoji("smile", "1f600")));
            when(model.getMessage()).thenReturn(message);
            var data = new ChatData("123", "channel", "message", "viewer-id", "Viewer", "viewer", "#0000ff", List.of(), message,
                    java.time.Instant.parse("2026-10-20T00:00:00Z"));
            when(model.getChatData()).thenReturn(data);
            when(model.isSystem()).thenReturn(kind.equals("system")); when(model.isSubs()).thenReturn(kind.startsWith("subscription"));
            when(model.isCheered()).thenReturn(kind.startsWith("cheer")); when(model.getBits()).thenReturn(100);
            var badge = borrowed(); var emote = borrowed(); var emoji = borrowed(); var profile = borrowed();
            when(model.getBadges()).thenReturn(FXCollections.observableArrayList(badge));
            var emotes = mock(ChatEmoteStore.class); when(model.getEmoteStore()).thenReturn(emotes); when(emotes.get("emote")).thenReturn(emote);
            when(model.getEmojiImage(any())).thenReturn(emoji);
            var channel = mock(TwitchChannelViewModel.class); when(model.getChannel()).thenReturn(channel); when(channel.getProfileImage()).thenReturn(profile);
            hidden.set(kind.equals("hidden")); deleted.set(kind.equals("deleted"));
            cell = new ChatDataCell(model, kind.equals("merged"), menus);
        }
        LazyImage borrowed() { var value = mock(LazyImage.class); when(value.valueProperty()).thenReturn(image.getReadOnlyProperty()); borrowed.add(value); return value; }
    }
}
