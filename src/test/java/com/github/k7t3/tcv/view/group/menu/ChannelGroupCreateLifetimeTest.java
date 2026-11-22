package com.github.k7t3.tcv.view.group.menu;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import javafx.collections.FXCollections;
import javafx.scene.control.TextInputDialog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelGroupCreateLifetimeTest {
    @Test
    void closingTheMenuEndsTheRealNestedModalLoop(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var dialog = new TextInputDialog();
            var menu = new ChannelGroupMenu(repository, FXCollections.observableArrayList(), () -> dialog);
            var fallback = new java.util.concurrent.atomic.AtomicBoolean();
            var guard = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
            guard.setOnFinished(event -> { fallback.set(true); dialog.close(); });
            dialog.setOnShown(event -> javafx.application.Platform.runLater(menu::close));
            try {
                guard.play(); menu.getItems().getFirst().fire();
                assertThat(fallback).isFalse(); assertThat(dialog.isShowing()).isFalse();
                assertThat(menu.getItems()).isEmpty(); verify(repository, never()).saveAsync(any());
            } finally { guard.stop(); menu.close(); dialog.close(); }
        });
    }

    @Test
    void endingTheParentMenuClosesItsDialogAndRejectsALateAcceptedName(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var dialog = mock(TextInputDialog.class);
            var owner = new AtomicReference<ChannelGroupMenu>();
            when(dialog.showAndWait()).thenAnswer(call -> { owner.get().close(); return Optional.of("Late"); });
            var menu = new ChannelGroupMenu(repository, FXCollections.observableArrayList(), () -> dialog); owner.set(menu);
            try {
                menu.getItems().getFirst().fire();
                verify(dialog).close(); verify(repository, never()).saveAsync(any());
            } finally { menu.close(); }
        });
    }

    @Test
    void aDialogReturnedAfterItsItemWasClosedIsNeverShown(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var dialog = mock(TextInputDialog.class);
            var owner = new AtomicReference<ChannelGroupCreateMenuItem>();
            var item = new ChannelGroupCreateMenuItem(repository, FXCollections.observableArrayList(), () -> { owner.get().close(); return dialog; }); owner.set(item);
            item.fire(); verify(dialog, never()).showAndWait(); verify(dialog).close(); verify(repository, never()).saveAsync(any());
        });
    }

    @Test
    void acceptingANameUsesTheChannelsSelectedWhenTheActionStarted(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var dialog = mock(TextInputDialog.class);
            var first = mock(TwitchChannelViewModel.class); var later = mock(TwitchChannelViewModel.class);
            var selected = FXCollections.observableArrayList(first);
            when(dialog.showAndWait()).thenAnswer(call -> { selected.setAll(later); return Optional.of("  My group  "); });
            var item = new ChannelGroupCreateMenuItem(repository, selected, () -> dialog);
            try {
                item.fire(); var saved = org.mockito.ArgumentCaptor.forClass(ChannelGroup.class); verify(repository).saveAsync(saved.capture());
                assertThat(saved.getValue().getName()).isEqualTo("  My group  ");
                assertThat(saved.getValue().getChannels()).containsExactly(first); verify(dialog).close();
            } finally { item.close(); }
        });
    }

    @Test
    void aClosedItemRejectsAnActionHandlerCapturedBeforeItsClose(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var factory = new AtomicInteger();
            var item = new ChannelGroupCreateMenuItem(repository, FXCollections.observableArrayList(), () -> { factory.incrementAndGet(); return mock(TextInputDialog.class); });
            var action = item.getOnAction();
            assertThat(action).isNotNull(); item.close();
            item.fire(); action.handle(new javafx.event.ActionEvent());
            assertThat(factory).hasValue(0); verify(repository, never()).saveAsync(any());
        });
    }

    @Test
    void reentrantFactoryActionsShareOneDialogAndSubmitOnlyOnce(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var dialog = mock(TextInputDialog.class);
            when(dialog.showAndWait()).thenReturn(Optional.of("Group"));
            var created = new AtomicInteger(); var owner = new AtomicReference<ChannelGroupCreateMenuItem>();
            var item = new ChannelGroupCreateMenuItem(repository, FXCollections.observableArrayList(), () -> {
                if (created.incrementAndGet() == 1) owner.get().fire(); return dialog;
            }); owner.set(item);
            try {
                item.fire(); assertThat(created).hasValue(1); verify(dialog, times(1)).showAndWait();
                verify(repository, times(1)).saveAsync(any()); verify(dialog, times(1)).close();
            } finally { item.close(); }
        });
    }

    @Test
    void aShowingFailurePreservesItsCauseAndTheCleanupFailure(FxRobot robot) {
        robot.interact(() -> {
            var repository = mock(ChannelGroupRepository.class); var dialog = mock(TextInputDialog.class);
            var original = new IllegalStateException("show failed"); var cleanup = new IllegalArgumentException("close failed");
            when(dialog.showAndWait()).thenThrow(original); doThrow(cleanup).when(dialog).close();
            var item = new ChannelGroupCreateMenuItem(repository, FXCollections.observableArrayList(), () -> dialog);
            try { assertThatThrownBy(item::fire).isSameAs(original).satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(cleanup)); }
            finally { item.close(); }
            verify(repository, never()).saveAsync(any());
        });
    }
}
