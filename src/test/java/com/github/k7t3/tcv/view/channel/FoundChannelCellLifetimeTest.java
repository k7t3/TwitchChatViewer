package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.FoundChannelViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class FoundChannelCellLifetimeTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void emptyOrClosedCellsDetachTheOldProfileBinding(boolean close, FxRobot robot) {
        robot.interact(() -> {
            var value = new ReadOnlyObjectWrapper<Image>(new WritableImage(2, 2));
            var item = item("first", value);
            var cell = new FoundChannelListCell();
            try {
                cell.updateItem(item, false);
                var image = (ImageView) ((HBox) cell.getGraphic()).getChildren().getFirst();
                if (close) cell.close(); else cell.updateItem(null, true);
                assertThat(image.imageProperty().isBound()).isFalse();
                assertThat(image.getImage()).isNull();
                value.set(new WritableImage(3, 3));
                assertThat(image.getImage()).isNull();
            } finally { cell.close(); }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aLateUnsupportedBrowserResultCannotOpenADialogFromAReusedOrClosedCell(boolean close, FxRobot robot) throws Exception {
        var alert = mock(Alert.class);
        var result = FXTask.task(() -> false);
        var cell = new FoundChannelListCell(() -> alert);
        robot.interact(() -> {
            var item = item("first", new ReadOnlyObjectWrapper<>());
            when(item.openChannelPageOnBrowser()).thenReturn(result);
            cell.updateItem(item, false);
            ((Button) ((HBox) cell.getGraphic()).getChildren().getLast()).fire();
            if (close) cell.close(); else cell.updateItem(item("next", new ReadOnlyObjectWrapper<>()), false);
        });
        try {
            result.run(); result.get(5, TimeUnit.SECONDS);
            robot.interact(() -> verify(alert, never()).show());
        } finally { robot.interact(cell::close); }
    }

    @org.junit.jupiter.api.Test
    void anAlreadyCompletedUnsupportedResultShowsOneOwnedWarning(FxRobot robot) throws Exception {
        var result = FXTask.task(() -> false);
        result.run(); result.get(5, TimeUnit.SECONDS);
        robot.interact(() -> {
            assertThat(result.getState()).isEqualTo(javafx.concurrent.Worker.State.SUCCEEDED);
            var alert = mock(Alert.class); var cell = new FoundChannelListCell(() -> alert);
            var item = item("first", new ReadOnlyObjectWrapper<>());
            when(item.openChannelPageOnBrowser()).thenReturn(result);
            try {
                cell.updateItem(item, false);
                ((Button) ((HBox) cell.getGraphic()).getChildren().getLast()).fire();
                verify(alert).show();
                cell.close(); verify(alert).hide();
            } finally { cell.close(); }
        });
    }

    @org.junit.jupiter.api.Test
    void aQueuedSuccessCannotWarnAfterTheModelEndsBeforeItsCellIsReused(FxRobot robot) {
        var alert = mock(Alert.class); var cell = new FoundChannelListCell(() -> alert);
        var result = FXTask.task(() -> false);
        var ended = new java.util.concurrent.atomic.AtomicBoolean();
        robot.interact(() -> {
            var item = item("first", new ReadOnlyObjectWrapper<>());
            when(item.isClosed()).thenAnswer(ignored -> ended.get());
            when(item.openChannelPageOnBrowser()).thenReturn(result);
            cell.updateItem(item, false);
            ((Button) ((HBox) cell.getGraphic()).getChildren().getLast()).fire();
            result.run(); ended.set(true);
        });
        try { robot.interact(() -> verify(alert, never()).show()); }
        finally { robot.interact(cell::close); }
    }

    private static FoundChannelViewModel item(String login, ReadOnlyObjectWrapper<Image> image) {
        var item = mock(FoundChannelViewModel.class);
        when(item.getBroadcaster()).thenReturn(new Broadcaster(login, login, login, "", ""));
        when(item.profileImageProperty()).thenReturn(image.getReadOnlyProperty());
        return item;
    }
}
