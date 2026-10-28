package com.github.k7t3.tcv.view.core;

import javafx.scene.control.Dialog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class OwnedDialogTest {
    @Test
    void anAcceptedResultIsReturnedAfterItsDialogIsReleased(FxRobot robot) {
        robot.interact(() -> {
            var dialog = dialog("Accepted"); var owner = new OwnedDialog<String>(() -> dialog);
            try {
                assertThat(owner.show(value -> value.setTitle("Title"))).contains("Accepted");
                verify(dialog).setTitle("Title"); verify(dialog).setResult(null); verify(dialog).close();
            } finally { owner.close(); }
            verify(dialog, times(1)).close();
        });
    }

    @Test
    void closingDuringTheModalLoopClosesTheDialogAndRejectsItsLateResult(FxRobot robot) {
        robot.interact(() -> {
            var dialog = dialog("Late"); var ref = new AtomicReference<OwnedDialog<String>>();
            when(dialog.showAndWait()).thenAnswer(call -> { ref.get().close(); return Optional.of("Late"); });
            var owner = new OwnedDialog<String>(() -> dialog); ref.set(owner);
            assertThat(owner.show(value -> {})).isEmpty(); verify(dialog, times(1)).close();
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void endingDuringFactoryCreationNeverShowsTheReturnedDialog(boolean permanently, FxRobot robot) {
        robot.interact(() -> {
            var dialog = dialog("Late"); var ref = new AtomicReference<OwnedDialog<String>>();
            var owner = new OwnedDialog<String>(() -> {
                if (permanently) ref.get().close(); else ref.get().cancel();
                return dialog;
            }); ref.set(owner);
            try {
                assertThat(owner.show(value -> {})).isEmpty(); verify(dialog, never()).showAndWait(); verify(dialog).close();
            } finally { owner.close(); }
        });
    }

    @Test
    void aClosedOwnerDoesNotInvokeItsFactory(FxRobot robot) {
        robot.interact(() -> {
            var owner = new OwnedDialog<String>(() -> { throw new AssertionError("closed factory invoked"); });
            owner.close(); owner.close(); assertThat(owner.show(value -> {})).isEmpty();
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void reentrantOpeningSharesOneFactoryAndModalLoop(boolean duringFactory, FxRobot robot) {
        robot.interact(() -> {
            var dialog = dialog("Accepted"); var ref = new AtomicReference<OwnedDialog<String>>();
            var created = new AtomicInteger(); var shown = new AtomicInteger();
            when(dialog.showAndWait()).thenAnswer(call -> {
                if (shown.incrementAndGet() == 1 && !duringFactory) ref.get().show(value -> {});
                return Optional.of("Accepted");
            });
            var owner = new OwnedDialog<String>(() -> {
                if (created.incrementAndGet() == 1 && duringFactory) ref.get().show(value -> {});
                return dialog;
            }); ref.set(owner);
            try {
                assertThat(owner.show(value -> {})).contains("Accepted");
                assertThat(created).hasValue(1); assertThat(shown).hasValue(1); verify(dialog, times(1)).close();
            } finally { owner.close(); }
        });
    }

    @Test
    void cancellationRejectsTheOldAnswerAndStillAllowsTheNextDialog(FxRobot robot) {
        robot.interact(() -> {
            var first = dialog("Old"); var second = dialog("New");
            var dialogs = new ArrayDeque<>(List.of(first, second));
            var ref = new AtomicReference<OwnedDialog<String>>();
            when(first.showAndWait()).thenAnswer(call -> { ref.get().cancel(); return Optional.of("Old"); });
            var owner = new OwnedDialog<String>(dialogs::remove); ref.set(owner);
            try {
                assertThat(owner.show(value -> {})).isEmpty();
                assertThat(owner.show(value -> {})).contains("New");
                verify(first).close(); verify(second).close();
            } finally { owner.close(); }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aFailurePreservesItsOriginalCauseAndTheCleanupFailure(boolean duringConfiguration, FxRobot robot) {
        robot.interact(() -> {
            var dialog = dialog("Unused");
            var original = new IllegalStateException("dialog failed"); var cleanup = new IllegalArgumentException("close failed");
            if (!duringConfiguration) when(dialog.showAndWait()).thenThrow(original);
            doThrow(cleanup).when(dialog).close();
            var owner = new OwnedDialog<String>(() -> dialog);
            try {
                assertThatThrownBy(() -> owner.show(value -> { if (duringConfiguration) throw original; }))
                        .isSameAs(original).satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(cleanup));
            } finally { owner.close(); }
        });
    }

    @SuppressWarnings("unchecked")
    private static Dialog<String> dialog(String result) {
        var dialog = (Dialog<String>) mock(Dialog.class); when(dialog.showAndWait()).thenReturn(Optional.of(result)); return dialog;
    }
}
