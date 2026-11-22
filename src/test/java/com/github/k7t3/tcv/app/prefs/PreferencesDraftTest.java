package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.keyboard.KeyBinding;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombinations;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.app.theme.ThemeType;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(ApplicationExtension.class)
class PreferencesDraftTest {
    static void applyCommitted(AppPreferences app, PreferencesViewModelBase draft) {
        var builder = new SettingsDraft.Builder();
        draft.prepare(builder);
        app.applyCommittedValues(builder.build().values());
    }

    @Test
    void applyingTheSameDraftBackToItsOpeningThemeMustUpdateTheCachedValue(FxRobot robot) {
        robot.interact(() -> {
            var app = new AppPreferences(new MemoryPreferences());
            var prefs = app.getGeneralPreferences();
            prefs.setTheme(Theme.PRIMER);
            var draft = new GeneralPreferencesViewModel(prefs);
            draft.setTheme(Theme.CUPERTINO);
            applyCommitted(app, draft);
            assertThat(prefs.getTheme()).isEqualTo(Theme.CUPERTINO);
            draft.setTheme(Theme.PRIMER);
            applyCommitted(app, draft);
            assertThat(prefs.getTheme()).isEqualTo(Theme.PRIMER);
        });
    }

    @Test
    void applyingBackToTheOpeningColorSchemeMustUpdateTheCachedValue(FxRobot robot) {
        robot.interact(() -> {
            var app = new AppPreferences(new MemoryPreferences());
            var prefs = app.getGeneralPreferences();
            prefs.setThemeType(ThemeType.LIGHTER);
            var draft = new GeneralPreferencesViewModel(prefs);
            draft.setThemeType(ThemeType.DARKER);
            applyCommitted(app, draft);
            draft.setThemeType(ThemeType.LIGHTER);
            applyCommitted(app, draft);
            assertThat(prefs.getThemeType()).isEqualTo(ThemeType.LIGHTER);
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"theme", "type", "open"})
    void missingRequiredChoicesAreRejectedBeforeAnyPreferenceIsChanged(String field, FxRobot robot) {
        robot.interact(() -> {
            var app = new AppPreferences(new MemoryPreferences());
            var prefs = app.getGeneralPreferences();
            prefs.setTheme(Theme.PRIMER);
            var draft = new GeneralPreferencesViewModel(prefs);
            draft.setTheme(Theme.CUPERTINO);
            switch (field) {
                case "theme" -> draft.setTheme(null);
                case "type" -> draft.setThemeType(null);
                case "open" -> draft.setChatOpenType(null);
            }
            assertThat(draft.canSync()).isFalse();
            assertThatThrownBy(() -> draft.prepare(new SettingsDraft.Builder())).isInstanceOf(IllegalStateException.class);
            assertThat(prefs.getTheme()).isEqualTo(Theme.PRIMER);
        });
    }

    @Test
    void preexistingDuplicateKeyBindingsAreInvalidAsSoonAsTheEditorOpens(FxRobot robot) {
        robot.interact(() -> {
            var prefs = new AppPreferences(new MemoryPreferences());
            var keys = new KeyBindingCombinations();
            var duplicate = KeyCombination.keyCombination("Shortcut+Alt+R");
            keys.updateCombination(KeyBinding.OPEN_PREFERENCES, duplicate);
            keys.updateCombination(KeyBinding.OPEN_SEARCH_VIEW, duplicate);
            var draft = new KeyBindingPreferencesViewModel(keys);
            assertThat(draft.canSync()).isFalse();
            assertThat(draft.getCombinationList().stream().filter(item -> item.isDuplicated()).count()).isEqualTo(2);
        });
    }
}
