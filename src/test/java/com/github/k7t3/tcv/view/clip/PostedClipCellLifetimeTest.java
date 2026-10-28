package com.github.k7t3.tcv.view.clip;

import com.github.k7t3.tcv.app.clip.PostedClipItem;
import com.github.k7t3.tcv.app.service.FXTask;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class PostedClipCellLifetimeTest {
    @Test
    void metadataArrivingAfterTheInitialRenderUpdatesOnlyTheCurrentCell(FxRobot robot) {
        robot.interact(() -> {
            var metadata = new javafx.beans.property.ReadOnlyObjectWrapper<com.github.k7t3.tcv.domain.clip.VideoClip>();
            var item = mock(PostedClipItem.class);
            when(item.getUrl()).thenReturn("https://clips.twitch.tv/first");
            when(item.clipProperty()).thenReturn(metadata.getReadOnlyProperty());
            when(item.getClip()).thenAnswer(ignored -> metadata.get());
            when(item.getTitle()).thenReturn("Loaded clip");
            var next = mock(PostedClipItem.class); when(next.getUrl()).thenReturn("https://clips.twitch.tv/next");
            var cell = new PostedClipViewCell();
            try {
                cell.updateItem(item, false);
                metadata.set(mock(com.github.k7t3.tcv.domain.clip.VideoClip.class));
                assertThat(cell.getGraphic().lookupAll(".label").stream().map(node -> ((Label) node).getText()))
                        .contains("Loaded clip").doesNotContain("UNKNOWN");
                cell.updateItem(next, false);
                metadata.set(mock(com.github.k7t3.tcv.domain.clip.VideoClip.class));
                assertThat(cell.getGraphic().lookupAll(".label").stream().map(node -> ((Label) node).getText()))
                        .contains("https://clips.twitch.tv/next").doesNotContain("Loaded clip");
                cell.close();
                metadata.set(mock(com.github.k7t3.tcv.domain.clip.VideoClip.class));
                assertThat(cell.getGraphic()).isNull();
            } finally { cell.close(); }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aLateRetryCannotRenderIntoAReusedOrClosedCell(boolean close, FxRobot robot) throws Exception {
        var first = mock(PostedClipItem.class); when(first.getUrl()).thenReturn("https://example.invalid/first");
        var next = mock(PostedClipItem.class); when(next.getUrl()).thenReturn("https://example.invalid/next");
        var retry = FXTask.task(() -> {}); doReturn(retry).when(first).retry();
        var cell = new PostedClipViewCell();
        robot.interact(() -> {
            cell.updateItem(first, false);
            ((Button) ((HBox) cell.getGraphic()).getChildren().get(5)).fire();
            if (close) cell.close(); else cell.updateItem(next, false);
        });
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) { worker.submit(retry).get(5, TimeUnit.SECONDS); }
        robot.interact(() -> {
            if (close) assertThat(cell.getGraphic()).isNull();
            else assertThat(cell.getGraphic().lookupAll(".label").stream().map(node -> ((Label) node).getText()))
                    .contains("https://example.invalid/next").doesNotContain("https://example.invalid/first");
            cell.close();
        });
    }

    @Test
    void aQueuedRetrySuccessDoesNotRequestImagesAfterRemovalBeforeCellReuse(FxRobot robot) {
        var removed = new java.util.concurrent.atomic.AtomicBoolean();
        var lateImages = new java.util.concurrent.atomic.AtomicInteger();
        var metadata = new javafx.beans.property.ReadOnlyObjectWrapper<com.github.k7t3.tcv.domain.clip.VideoClip>();
        var item = mock(PostedClipItem.class);
        when(item.getUrl()).thenReturn("https://clips.twitch.tv/first");
        when(item.clipProperty()).thenReturn(metadata.getReadOnlyProperty());
        when(item.getClip()).thenAnswer(ignored -> metadata.get());
        when(item.getThumbnailImage()).thenAnswer(ignored -> { if (removed.get()) lateImages.incrementAndGet(); return null; });
        doAnswer(ignored -> { removed.set(true); return null; }).when(item).remove();
        var retry = FXTask.task(() -> metadata.set(mock(com.github.k7t3.tcv.domain.clip.VideoClip.class)));
        doReturn(retry).when(item).retry();
        var cell = new PostedClipViewCell();
        robot.interact(() -> {
            cell.updateItem(item, false);
            ((Button) ((HBox) cell.getGraphic()).getChildren().get(5)).fire();
            retry.run();
            ((Button) ((HBox) cell.getGraphic()).getChildren().get(4)).fire();
        });
        try { robot.interact(() -> assertThat(lateImages).hasValue(0)); }
        finally { robot.interact(cell::close); }
    }

    @Test void anEmptyCellClearsItsPreviousImageReference(FxRobot robot) {
        robot.interact(() -> {
            var item = mock(PostedClipItem.class); when(item.getUrl()).thenReturn("https://example.invalid/clip");
            var cell = new PostedClipViewCell(); cell.updateItem(item, false);
            var thumbnail = (com.github.k7t3.tcv.view.image.LazyImageView) ((HBox) cell.getGraphic()).getChildren().getFirst();
            cell.updateItem(null, true);
            assertThat(thumbnail.getImage()).isNull();
            cell.close();
        });
    }
}
