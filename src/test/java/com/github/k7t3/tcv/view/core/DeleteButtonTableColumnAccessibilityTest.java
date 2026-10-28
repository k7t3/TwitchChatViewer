package com.github.k7t3.tcv.view.core;

import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.util.ArrayList;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.AccessibleAttribute;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(ApplicationExtension.class)
class DeleteButtonTableColumnAccessibilityTest {
    @Test
    void aVisibleDeleteButtonExposesItsPurposeToAccessibility(FxRobot robot) {
        robot.interact(() -> {
            var cells = new ArrayList<DeleteButtonTableColumn<String>>();
            var table = new TableView<>(FXCollections.observableArrayList("keyword"));
            var column = new TableColumn<String, String>();
            column.setCellValueFactory(data -> new ReadOnlyStringWrapper(data.getValue()));
            column.setCellFactory(ignored -> {
                var cell = new DeleteButtonTableColumn<String>(value -> {});
                cells.add(cell);
                return cell;
            });
            table.getColumns().add(column);
            var window = new Stage();
            try {
                window.setScene(new Scene(table, 320, 240));
                window.show();
                table.applyCss(); table.layout();
                var buttons = table.lookupAll(".table-cell .button").stream().map(Button.class::cast).toList();

                assertThat(buttons).singleElement().satisfies(button ->
                        assertThat(button.queryAccessibleAttribute(AccessibleAttribute.TEXT))
                                .isEqualTo(Resources.getString("menu.remove")));
            } finally {
                CloseActions.runAll(() -> cells.forEach(DeleteButtonTableColumn::close), window::close);
            }
        });
    }
}
