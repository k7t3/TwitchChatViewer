package com.github.k7t3.tcv.view.clip;

import com.github.k7t3.tcv.app.clip.PostedClipItem;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(ApplicationExtension.class)
class PostedClipImageViewTest {

    @Test
    void ownerButtonDisplaysTheImageProvidedByTheRepository(FxRobot robot) {
        robot.interact(() -> {
            var url = "file:/missing-owner.png";
            var value = new ReadOnlyObjectWrapper<Image>();
            var image = mock(LazyImage.class);
            when(image.valueProperty()).thenReturn(value.getReadOnlyProperty());
            when(image.completion()).thenReturn(new CompletableFuture<>());
            var images = mock(ImageLoader.class);
            when(images.load(url, 30, 30)).thenReturn(image);
            var repository = new PostedClipRepository(images, Runnable::run, ignored -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
            var owner = new Broadcaster("123", "owner", "Owner", url, "");
            var source = new com.github.k7t3.tcv.support.ClipEventSource(repository,
                    mock(com.github.k7t3.tcv.domain.Twitch.class, org.mockito.Mockito.RETURNS_DEEP_STUBS), owner);
            source.post("clip", null);
            var controller = new PostedClipRepositoryView();
            try {
                var view = FluentViewLoader.fxmlView(PostedClipRepositoryView.class).codeBehind(controller).viewModel(repository)
                        .resourceBundle(Resources.getResourceBundle()).load().getView();
                var owners = (HBox) view.lookup("#channelOwnersContainer");
                assertThat(owners.getChildren()).hasSize(1);
                var button = (ToggleButton) owners.getChildren().getFirst();
                var graphic = (ImageView) button.getGraphic();

                var decoded = new WritableImage(2, 2);
                value.set(decoded);

                assertThat(graphic.getImage()).isSameAs(decoded);
                controller.close();
                value.set(new WritableImage(3, 3));
                assertThat(graphic.getImage()).isNull();
                assertThat(repository.isClosed()).isFalse();
            } finally {
                controller.close();
                repository.close();
            }
        });
    }
}
