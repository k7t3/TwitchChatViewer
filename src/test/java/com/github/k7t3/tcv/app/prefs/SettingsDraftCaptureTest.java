package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.chat.filter.UserFilterEntry;
import com.github.k7t3.tcv.app.theme.Theme;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class SettingsDraftCaptureTest {
    @Test void capturingAllChangesLeavesApplicationModelsAndStorageUntouched(FxRobot robot) {
        robot.interact(() -> {
            var node = new MemoryPreferences();
            node.put("theme", "PRIMER");
            var prefs = new AppPreferences(node);
            var store = mock(ChatFilterEntityStore.class);
            var filters = new ChatFilters(store, Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
            var keyword = KeywordFilterEntry.containsMatch("original");
            keyword.setFilterId(7);
            filters.getKeywordEntries().add(keyword);
            var user = new UserFilterEntry("42", "Name", "original comment");
            filters.getUserEntries().add(user);
            try {
                var general = new GeneralPreferencesViewModel(prefs.getGeneralPreferences());
                general.setTheme(Theme.NORD);
                var keywords = new KeywordFilterViewModel(filters);
                keywords.getKeywordEntries().getFirst().setKeyword("accepted");
                var users = new UserFilterViewModel(filters);
                users.getUsers().getFirst().setComment("accepted comment");
                var builder = new SettingsDraft.Builder();
                general.prepare(builder);
                keywords.prepare(builder);
                users.prepare(builder);
                var accepted = builder.build();
                keywords.getKeywordEntries().getFirst().setKeyword("later");
                users.getUsers().getFirst().setComment("later");
                general.close();
                assertThat(accepted.values().get("theme")).contains("NORD");
                assertThat(accepted.filters().keywords().getFirst().keyword()).isEqualTo("accepted");
                assertThat(accepted.filters().users().getFirst().comment()).isEqualTo("accepted comment");
                assertThat(keyword.getKeyword()).isEqualTo("original");
                assertThat(user.getComment()).isEqualTo("original comment");
                assertThat(node.get("theme", null)).isEqualTo("PRIMER");
                verifyNoInteractions(store);
            } finally { filters.close(); }
        });
    }

    @Test void unchangedFiltersAndANewlyRemovedUnsavedKeywordProduceNoSqlChanges(FxRobot robot) {
        robot.interact(() -> {
            var filters = new ChatFilters(mock(ChatFilterEntityStore.class), Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
            try {
                var existing = KeywordFilterEntry.containsMatch("existing");
                existing.setFilterId(7);
                filters.getKeywordEntries().add(existing);
                filters.getUserEntries().add(new UserFilterEntry("42", "Name", "comment"));
                var keywords = new KeywordFilterViewModel(filters);
                keywords.addEntry(KeywordFilterEntry.containsMatch("new"));
                keywords.getKeywordEntries().getLast().setRemoved(true);
                var builder = new SettingsDraft.Builder();
                keywords.prepare(builder);
                new UserFilterViewModel(filters).prepare(builder);
                assertThat(builder.build().filters()).isEqualTo(ChatFilterEntityStore.Changes.empty());
            } finally { filters.close(); }
        });
    }

    @Test void returningAKeyToItsDefaultCapturesARemovalWithoutChangingLiveBindings(FxRobot robot) {
        robot.interact(() -> {
            var binding = com.github.k7t3.tcv.app.keyboard.KeyBinding.OPEN_PREFERENCES;
            var keys = new com.github.k7t3.tcv.app.keyboard.KeyBindingCombinations();
            var custom = javafx.scene.input.KeyCombination.keyCombination("Shortcut+Alt+R");
            keys.updateCombination(binding, custom);
            var draft = new KeyBindingPreferencesViewModel(keys);
            draft.getCombinationList().stream().filter(item -> item.getBinding() == binding).findFirst().orElseThrow().resetKeyCombination();
            var builder = new SettingsDraft.Builder();
            draft.prepare(builder);
            assertThat(builder.build().values().get(binding.getId())).isEmpty();
            assertThat(keys.getAllBindings().stream().filter(item -> item.getBinding() == binding).findFirst().orElseThrow().getCombination()).isEqualTo(custom);
        });
    }
}
