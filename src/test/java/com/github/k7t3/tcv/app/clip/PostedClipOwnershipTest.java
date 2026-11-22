package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class PostedClipOwnershipTest {
    @Test
    void anOldItemCannotRemoveItsReplacementWithTheSameId(FxRobot robot) {
        robot.interact(() -> {
            var repository = new PostedClipRepository(mock(ImageLoader.class), Runnable::run, ignored -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
            var source = new com.github.k7t3.tcv.support.ClipEventSource(repository,
                    mock(com.github.k7t3.tcv.domain.Twitch.class, RETURNS_DEEP_STUBS),
                    new Broadcaster("123", "user", "User", "https://images.invalid/user", ""));
            try {
                var old = source.post("clip", null);
                old.remove();
                var replacement = source.post("clip", null);
                old.remove();
                assertThat(repository.getItems()).containsEntry("clip", replacement);
            } finally { repository.close(); }
        });
    }

    @Test
    void aClosedRepositoryCannotCreateAnotherBroadcasterImageStore(FxRobot robot) {
        robot.interact(() -> {
            var images = mock(ImageLoader.class);
            var repository = new PostedClipRepository(images, Runnable::run, ignored -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
            repository.close();
            var owner = new Broadcaster("123", "user", "User", "https://images.invalid/user", "");
            assertThatThrownBy(() -> repository.getBroadcasterImage(owner)).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(images);
        });
    }
}
