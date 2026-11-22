package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatDataContextMenuLifetimeTest {
    @Test
    void closingTheMenuDetachesItsRetainedActionsAndHidesThePopup(FxRobot robot) {
        robot.interact(() -> {
            var model = model(); var menu = new ChatDataContextMenu(model);
            var stage = new Stage(); stage.setScene(new Scene(new StackPane(), 300, 180)); stage.show();
            try {
                menu.show(stage, 10, 10); var items = List.copyOf(menu.getItems());
                menu.getItems().getFirst().fire(); verify(model).copyMessage(); clearInvocations(model);
                menu.close(); menu.close();
                assertThat(menu.isShowing()).isFalse(); assertThat(menu.getItems()).isEmpty();
                for (var item : items) assertThat(item.getOnAction()).isNull();
                items.getFirst().fire(); verify(model, never()).copyMessage();
            } finally { menu.close(); menu.hide(); stage.close(); stage.setScene(null); }
        });
    }

    @Test
    void endingDuringConfirmationClosesTheDialogAndRejectsItsLateAcceptedAnswer(FxRobot robot) {
        robot.interact(() -> {
            var model = model(); var dialog = mock(TextInputDialog.class); var reference = new AtomicReference<ChatDataContextMenu>();
            when(dialog.showAndWait()).thenAnswer(ignored -> { reference.get().close(); return Optional.of(" late comment "); });
            var menu = new ChatDataContextMenu(model, () -> dialog); reference.set(menu);
            try {
                userFilter(menu).fire(); verify(dialog).close(); verify(model, never()).userFilter(anyString());
                assertThat(menu.getItems()).isEmpty();
            } finally { menu.close(); }
        });
    }

    @Test
    void aDialogReturnedAfterTheFactoryEndedItsOwnerIsClosedWithoutShowing(FxRobot robot) {
        robot.interact(() -> {
            var model = model(); var dialog = mock(TextInputDialog.class); var reference = new AtomicReference<ChatDataContextMenu>();
            var menu = new ChatDataContextMenu(model, () -> { reference.get().close(); return dialog; }); reference.set(menu);
            try {
                userFilter(menu).fire(); verify(dialog, never()).showAndWait(); verify(dialog).close(); verify(model, never()).userFilter(anyString());
            } finally { menu.close(); }
        });
    }

    @ParameterizedTest @CsvSource({"'  note  ',note", "'   ',viewer"})
    void acceptedConfirmationKeepsTheExistingCommentNormalization(String input, String expected, FxRobot robot) {
        robot.interact(() -> {
            var model = model(); var dialog = mock(TextInputDialog.class); when(dialog.showAndWait()).thenReturn(Optional.of(input));
            var menu = new ChatDataContextMenu(model, () -> dialog);
            try { userFilter(menu).fire(); verify(model).userFilter(expected); }
            finally { menu.close(); }
        });
    }

    @Test
    void aShowingFailureKeepsItsOriginalCauseEvenWhenDialogCleanupAlsoFails(FxRobot robot) {
        robot.interact(() -> {
            var model = model(); var dialog = mock(TextInputDialog.class);
            var original = new IllegalStateException("dialog could not show"); var cleanup = new IllegalArgumentException("dialog could not close");
            when(dialog.showAndWait()).thenThrow(original); doThrow(cleanup).when(dialog).close();
            var menu = new ChatDataContextMenu(model, () -> dialog);
            try {
                assertThatThrownBy(() -> userFilter(menu).fire()).isSameAs(original)
                        .satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(cleanup));
                verify(model, never()).userFilter(anyString());
            } finally { menu.close(); }
        });
    }

    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void reentrantActionsShareOneConfirmationAndSubmitOnlyOnce(boolean duringFactory, FxRobot robot) {
        robot.interact(() -> {
            var model = model(); var dialog = mock(TextInputDialog.class); var menuRef = new AtomicReference<ChatDataContextMenu>();
            var created = new java.util.concurrent.atomic.AtomicInteger(); var shown = new java.util.concurrent.atomic.AtomicInteger();
            when(dialog.showAndWait()).thenAnswer(ignored -> {
                if (shown.incrementAndGet() == 1 && !duringFactory) userFilter(menuRef.get()).fire();
                return Optional.of("note");
            });
            var menu = new ChatDataContextMenu(model, () -> {
                if (created.incrementAndGet() == 1 && duringFactory) userFilter(menuRef.get()).fire();
                return dialog;
            }); menuRef.set(menu);
            try {
                userFilter(menu).fire(); assertThat(created).hasValue(1); assertThat(shown).hasValue(1);
                verify(model, times(1)).userFilter("note"); verify(dialog, times(1)).close();
            } finally { menu.close(); }
        });
    }

    private static ChatDataViewModel model() { var model = mock(ChatDataViewModel.class); when(model.getUserName()).thenReturn("viewer"); return model; }
    private static MenuItem userFilter(ChatDataContextMenu menu) {
        return menu.getItems().stream().filter(item -> Resources.getString("chat.menu.filter.user").equals(item.getText())).findFirst().orElseThrow();
    }
}
