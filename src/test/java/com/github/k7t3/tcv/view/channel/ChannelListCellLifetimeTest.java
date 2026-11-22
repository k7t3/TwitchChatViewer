package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.view.image.LazyImageView;
import javafx.beans.property.*;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelListCellLifetimeTest {
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void oldMetadataCannotChangeAReusedOrClosedCell(boolean close, FxRobot robot) {
        robot.interact(() -> {
            var first = new Item("first"); var next = new Item("next"); next.follow.set(true);
            var cell = new Cell();
            try {
                cell.render(first.model);
                var oldImage = (LazyImageView) ((BorderPane) cell.getGraphic()).getLeft();
                if (close) cell.close();
                cell.render(next.model);
                first.title.set("late title"); first.game.set("late game"); first.follow.set(true);
                first.image.set(new WritableImage(4,4));
                if (close) {
                    assertThat(cell.getGraphic()).isNull(); assertThat(cell.getTooltip()).isNull();
                    assertThat(oldImage.getImage()).isNull(); verify(next.model, never()).profileImageProperty();
                } else {
                    var layout = (BorderPane) cell.getGraphic();
                    assertThat(((LazyImageView) layout.getLeft()).getImage()).isSameAs(next.image.get());
                    assertThat(((Label) ((VBox) layout.getCenter()).getChildren().get(1)).getText()).isEqualTo("game next");
                    assertThat(cell.getTooltip().getText()).isEqualTo("title next");
                    assertThat(cell.getPseudoClassStates()).doesNotContain(javafx.css.PseudoClass.getPseudoClass("unfollow"));
                }
                verify(first.model, never()).close(); verify(first.lazy, never()).close();
                verify(next.model, never()).close(); verify(next.lazy, never()).close();
            } finally { cell.close(); }
        });
    }

    @Test
    void theCurrentStreamsTitleUpdatesItsTooltip(FxRobot robot) {
        robot.interact(() -> {
            var item = new Item("first"); var cell = new Cell();
            try {
                cell.render(item.model); var tooltip = cell.getTooltip();
                item.title.set("updated title");
                assertThat(tooltip.getText()).isEqualTo("updated title");
                cell.render(null);
                assertThat(tooltip.textProperty().isBound()).isFalse();
                assertThat(tooltip.getText()).isEmpty();
            } finally { cell.close(); }
        });
    }

    @Test
    void followingChangesOnlyAffectTheCurrentCell(FxRobot robot) {
        robot.interact(() -> {
            var item = new Item("first"); var cell = new Cell();
            var unfollow = javafx.css.PseudoClass.getPseudoClass("unfollow");
            try {
                cell.render(item.model); assertThat(cell.getPseudoClassStates()).contains(unfollow);
                item.follow.set(true);
                assertThat(cell.getPseudoClassStates()).doesNotContain(unfollow);
                cell.render(null); item.follow.set(false);
                assertThat(cell.getPseudoClassStates()).doesNotContain(unfollow);
            } finally { cell.close(); }
        });
    }

    @Test
    void emptyCellsDetachThePreviousChannelsBindings(FxRobot robot) {
        robot.interact(() -> {
            var item = new Item("first"); var cell = new Cell(); cell.render(item.model);
            var layout = (BorderPane) cell.getGraphic(); var image = (LazyImageView) layout.getLeft();
            var game = (Label) ((VBox) layout.getCenter()).getChildren().get(1);
            assertThat(image.getImage()).isSameAs(item.image.get());
            assertThat(game.getText()).isEqualTo("game first");

            cell.render(null);

            assertThat(image.lazyImageProperty().isBound()).isFalse();
            assertThat(image.imageProperty().isBound()).isFalse(); assertThat(image.getImage()).isNull();
            assertThat(game.textProperty().isBound()).isFalse(); assertThat(game.getText()).isNull();
            item.game.set("late"); item.image.set(new WritableImage(3,3));
            assertThat(image.getImage()).isNull(); assertThat(game.getText()).isNull();
            assertThat(cell.getGraphic()).isNull(); assertThat(cell.getTooltip()).isNull();
            verify(item.model, never()).close(); verify(item.lazy, never()).close();
        });
    }

    static final class Cell extends TwitchChannelListCell {
        void render(TwitchChannelViewModel item) { updateItem(item, item == null); }
    }

    static final class Item {
        final TwitchChannelViewModel model = mock(TwitchChannelViewModel.class);
        final LazyImage lazy = mock(LazyImage.class);
        final ReadOnlyObjectWrapper<Image> image = new ReadOnlyObjectWrapper<>(new WritableImage(2,2));
        final StringProperty game;
        final StringProperty title;
        final ReadOnlyBooleanWrapper live = new ReadOnlyBooleanWrapper(true);
        final ReadOnlyBooleanWrapper follow = new ReadOnlyBooleanWrapper(false);
        final SimpleObjectProperty<Integer> viewers = new SimpleObjectProperty<>(1000);
        final BooleanProperty persistent = new SimpleBooleanProperty(true);
        Item(String name) {
            game = new SimpleStringProperty("game " + name); title = new SimpleStringProperty("title " + name);
            when(lazy.valueProperty()).thenReturn(image.getReadOnlyProperty());
            when(model.profileImageProperty()).thenReturn(new ReadOnlyObjectWrapper<>(lazy).getReadOnlyProperty());
            when(model.getUserName()).thenReturn(name); when(model.getUserLogin()).thenReturn(name);
            when(model.observableGameName()).thenReturn(game); when(model.observableStreamTitle()).thenReturn(title);
            when(model.getStreamTitle()).thenAnswer(ignored -> title.get());
            when(model.observableViewerCount()).thenReturn(viewers);
            when(model.getViewerCount()).thenAnswer(ignored -> viewers.get());
            when(model.liveProperty()).thenReturn(live.getReadOnlyProperty());
            when(model.followingProperty()).thenReturn(follow.getReadOnlyProperty());
            when(model.isFollowing()).thenAnswer(ignored -> follow.get());
            when(model.persistentProperty()).thenReturn(persistent); when(model.isPersistent()).thenReturn(true);
            when(model.isLive()).thenAnswer(ignored -> live.get());
        }
    }
}
