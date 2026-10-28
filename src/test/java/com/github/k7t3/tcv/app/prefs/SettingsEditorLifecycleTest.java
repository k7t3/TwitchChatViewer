package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.app.theme.ThemeManager;
import com.github.k7t3.tcv.app.theme.ThemeType;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class SettingsEditorLifecycleTest {
    @Test
    void queuedFontBatchesCannotRefillTheListAfterTheEditorCloses(FxRobot robot) throws Exception {
        var reference = new AtomicReference<ChatPreferencesViewModel>();
        var closed = new CountDownLatch(1);
        var beforeClose = new java.util.concurrent.atomic.AtomicInteger();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                var model = new ChatPreferencesViewModel(new AppPreferences(new MemoryPreferences()).getChatPreferences(),
                        worker, () -> java.util.Collections.nCopies(251, "System"), failure -> { throw new AssertionError(failure); });
                reference.set(model);
                model.getFontFamilies().addListener((ListChangeListener<FontFamily>) change -> {
                    if (!model.getFontFamilies().isEmpty()) Platform.runLater(() -> {
                        beforeClose.set(model.getFontFamilies().size());
                        model.close();
                        closed.countDown();
                    });
                });
                model.loadFontsAsync();
            });
            assertThat(closed.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(beforeClose.get()).isGreaterThan(0).isLessThan(251);
            robot.interact(() -> {
                assertThat(reference.get().getFontFamilies()).isEmpty();
                assertThat(reference.get().fontsReadyProperty().get()).isFalse();
            });
        } finally { if (reference.get() != null) robot.interact(reference.get()::close); }
    }

    @Test
    void closingAnUnacceptedThemePreviewRestoresBothValuesAndDisconnectsTheDraft(FxRobot robot) {
        robot.interact(() -> {
            var app = new AppPreferences(new MemoryPreferences());
            var prefs = app.getGeneralPreferences();
            prefs.setTheme(Theme.PRIMER);
            prefs.setThemeType(ThemeType.LIGHTER);
            try (var theme = new ThemeManager()) {
                theme.setTheme(prefs.getTheme());
                theme.setThemeType(prefs.getThemeType());
                var draft = new GeneralPreferencesViewModel(prefs, theme);
                draft.setTheme(Theme.NORD);
                draft.setThemeType(ThemeType.DARKER);
                assertThat(theme.getTheme()).isEqualTo(Theme.NORD);
                assertThat(theme.getThemeType()).isEqualTo(ThemeType.DARKER);
                draft.close();
                draft.setTheme(Theme.CUPERTINO);
                draft.setThemeType(ThemeType.SYSTEM);
                assertThat(theme.getTheme()).isEqualTo(Theme.PRIMER);
                assertThat(theme.getThemeType()).isEqualTo(ThemeType.LIGHTER);
                assertThat(prefs.getTheme()).isEqualTo(Theme.PRIMER);
                assertThat(draft.canSync()).isFalse();
            }
        });
    }

    @Test
    void acceptedThemePreviewRemainsAppliedWhenTheEditorCloses(FxRobot robot) {
        robot.interact(() -> {
            var app = new AppPreferences(new MemoryPreferences());
            var prefs = app.getGeneralPreferences();
            try (var theme = new ThemeManager()) {
                var draft = new GeneralPreferencesViewModel(prefs, theme);
                draft.setTheme(Theme.NORD);
                draft.setThemeType(ThemeType.DARKER);
                PreferencesDraftTest.applyCommitted(app, draft);
                draft.close();
                assertThat(theme.getTheme()).isEqualTo(Theme.NORD);
                assertThat(theme.getThemeType()).isEqualTo(ThemeType.DARKER);
                assertThat(prefs.getTheme()).isEqualTo(Theme.NORD);
            }
        });
    }

    @Test
    void aClosedFontEditorCancelsItsRequestAndDiscardsANonInterruptibleResult(FxRobot robot) throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var returned = new CountDownLatch(1);
        var reference = new AtomicReference<ChatPreferencesViewModel>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                robot.interact(() -> {
                    var prefs = new AppPreferences(new MemoryPreferences()).getChatPreferences();
                    var model = new ChatPreferencesViewModel(prefs, worker, () -> {
                        assertThat(Platform.isFxApplicationThread()).isFalse();
                        entered.countDown();
                        while (true) {
                            try { assertThat(release.await(5, TimeUnit.SECONDS)).isTrue(); break; }
                            catch (InterruptedException ignored) { }
                        }
                        returned.countDown();
                        return List.of("System");
                    }, failure -> { throw new AssertionError(failure); });
                    reference.set(model);
                    model.loadFontsAsync();
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> { reference.get().close(); assertThat(reference.get().isClosed()).isTrue(); });
                release.countDown();
                assertThat(returned.await(5, TimeUnit.SECONDS)).isTrue();
            } finally { release.countDown(); }
        } finally {
            if (reference.get() != null) robot.interact(reference.get()::close);
        }
        robot.interact(() -> assertThat(reference.get().getFontFamilies()).isEmpty());
    }

    @Test
    void loadedFontFamiliesArePublishedOnFxAndTheOwnedListIsClearedOnClose(FxRobot robot) throws Exception {
        var reference = new AtomicReference<ChatPreferencesViewModel>();
        var published = new CountDownLatch(1);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                var model = new ChatPreferencesViewModel(new AppPreferences(new MemoryPreferences()).getChatPreferences(),
                        worker, () -> List.of("System", "Serif"), failure -> { throw new AssertionError(failure); });
                reference.set(model);
                model.getFontFamilies().addListener((ListChangeListener<FontFamily>) change -> {
                    assertThat(Platform.isFxApplicationThread()).isTrue();
                    published.countDown();
                });
                model.loadFontsAsync();
            });
            assertThat(published.await(5, TimeUnit.SECONDS)).isTrue();
            robot.interact(() -> assertThat(reference.get().getFontFamilies()).extracting(FontFamily::getFamily).containsExactly("System", "Serif"));
        } finally { robot.interact(reference.get()::close); }
        robot.interact(() -> assertThat(reference.get().getFontFamilies()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null-font", "blank-font", "nan-size", "zero-size", "cache"})
    void invalidChatDraftCannotWritePartialSettings(String invalid, FxRobot robot) {
        robot.interact(() -> {
            var prefs = new AppPreferences(new MemoryPreferences()).getChatPreferences();
            var model = new ChatPreferencesViewModel(prefs, task -> { throw new AssertionError("no font request expected"); },
                    failure -> { throw new AssertionError(failure); });
            try {
                model.setShowUserName(false);
                switch (invalid) {
                    case "null-font" -> model.setFont(null);
                    case "blank-font" -> model.setFont(new FontFamily(""));
                    case "nan-size" -> model.setFontSize(Double.NaN);
                    case "zero-size" -> model.setFontSize(0);
                    case "cache" -> model.setChatCacheSize(0);
                }
                assertThat(model.canSync()).isFalse();
                assertThatThrownBy(() -> model.prepare(new SettingsDraft.Builder())).isInstanceOf(IllegalStateException.class);
                assertThat(prefs.isShowUserName()).isTrue();
                assertThat(prefs.getChatCacheSize()).isEqualTo(256);
            } finally { model.close(); }
        });
    }
}
