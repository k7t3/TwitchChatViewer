package com.github.k7t3.tcv.view.prefs;

import com.github.k7t3.tcv.app.prefs.KeywordFilterViewModel.Wrapper;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Cell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.util.Subscription;
import javafx.util.converter.DefaultStringConverter;

/** 一つのセルが現在表示する行の検証状態だけを監視する。 */
final class KeywordFilterCell extends TextFieldTableCell<Wrapper, String> implements AutoCloseable {
    private final Subscription validation;
    private boolean closed;

    KeywordFilterCell() {
        super(new DefaultStringConverter());
        getStyleClass().add("keyword-filter-cell");
        var invalid = tableRowProperty().flatMap(Cell::itemProperty).flatMap(Wrapper::invalidProperty).orElse(false);
        var visibleError = Bindings.createBooleanBinding(
                () -> !isEmpty() && invalid.getValue(), emptyProperty(), invalid);
        validation = JavaFXHelper.registerPseudoClass(this, "invalid", visibleError);
    }

    @Override public void startEdit() { if (!closed) super.startEdit(); }
    @Override public void commitEdit(String value) { if (!closed) super.commitEdit(value); }
    @Override public void cancelEdit() {
        super.cancelEdit();
        if (closed) { setText(null); setGraphic(null); }
    }
    @Override public void updateItem(String value, boolean empty) {
        super.updateItem(closed ? null : value, closed || empty);
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        validation.unsubscribe();
        cancelEdit();
        JavaFXHelper.updatePseudoClass(this, "invalid", false);
        updateTableRow(null);
        updateItem(null, true);
    }
}
