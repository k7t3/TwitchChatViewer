package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.app.image.ImageTestDoubles;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class FoundChannelJoinTest {
    @Test
    void joiningUsesTheRequestAlreadyStartedByItsRepository(FxRobot robot) {
        robot.interact(() -> {
            var owner = new Broadcaster("123", "channel", "Channel", "profile", "");
            var repository = mock(ChannelViewModelRepository.class);
            var request = spy(FXTask.task(() -> mock(TwitchChannelViewModel.class)));
            when(repository.getChannelAsync(owner)).thenReturn(request);
            doAnswer(invocation -> { throw new AssertionError("repository request was submitted twice"); })
                    .when(request).run();
            var model = new FoundChannelViewModel(repository,
                    new FoundChannel(owner, true, "Game"), ImageTestDoubles.emptyLoader(), failure -> { throw new AssertionError(failure); }, mock(BrowserActions.class));
            try {
                assertThat(model.joinChatAsync()).isNotSameAs(request);
                verify(request, never()).run();
                verify(repository).getChannelAsync(owner);
            } finally {
                model.close();
            }
        });
    }
}
