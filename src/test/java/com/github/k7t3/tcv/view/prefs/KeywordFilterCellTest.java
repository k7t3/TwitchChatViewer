package com.github.k7t3.tcv.view.prefs;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.prefs.KeywordFilterViewModel;
import com.github.k7t3.tcv.entity.service.ChatFilterEntityStore;
import javafx.css.PseudoClass;
import javafx.scene.control.TableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(ApplicationExtension.class)
class KeywordFilterCellTest {
    @Test
    void anEmptyCellDoesNotDisplayThePreviousRowsValidationError(FxRobot robot) {
        robot.interact(() -> {
            var filters = new ChatFilters(mock(ChatFilterEntityStore.class), Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
            var model = new KeywordFilterViewModel(filters); model.addEntry(KeywordFilterEntry.containsMatch(""));
            var row = new Row(); row.show(model.getKeywordEntries().getFirst());
            var cell = new KeywordFilterCell();
            try {
                cell.updateTableRow(row); cell.updateItem("", false);
                var invalid = PseudoClass.getPseudoClass("invalid");
                assertThat(cell.getPseudoClassStates()).contains(invalid);

                cell.updateItem(null, true);

                assertThat(cell.getPseudoClassStates()).doesNotContain(invalid);
                assertThat(cell.getText()).isNull(); assertThat(cell.getGraphic()).isNull();
            } finally { cell.close(); filters.close(); }
        });
    }

    static class Row extends TableRow<KeywordFilterViewModel.Wrapper> {
        void show(KeywordFilterViewModel.Wrapper value) { updateItem(value, value == null); }
    }
}
