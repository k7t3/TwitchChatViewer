package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.TwitchChannelListViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.ClientSessions;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class MainModelFactoryTest {
    @Test
    void failedChildConstructionReleasesEarlierModelsWithoutClosingBorrowedServices(FxRobot robot) {
        robot.interact(() -> {
            var helper = mock(AppHelper.class);
            when(helper.getExecutor()).thenReturn(Runnable::run);
            when(helper.getSubscribers()).thenReturn(mock(EventSubscribers.class));
            when(helper.getClientSessions()).thenReturn(mock(ClientSessions.class));
            var groups = mock(ChannelGroupRepository.class);
            var images = mock(ImageLoader.class);
            var failure = new IllegalStateException("clip model cannot be created");
            try (var channels = mockConstruction(ChannelViewModelRepository.class);
                 var chats = mockConstruction(ChatRoomContainerViewModel.class);
                 var lists = mockConstruction(TwitchChannelListViewModel.class);
                 var clips = mockConstruction(PostedClipRepository.class, (model, context) -> { throw failure; })) {

                assertThatThrownBy(() -> MainModelFactory.create(helper, groups, images,
                        unexpected -> { throw new AssertionError(unexpected); })).hasRootCause(failure);

                assertThat(channels.constructed()).hasSize(1);
                assertThat(chats.constructed()).hasSize(1);
                assertThat(lists.constructed()).hasSize(1);
                verify(channels.constructed().getFirst()).close();
                verify(chats.constructed().getFirst()).close();
                verify(lists.constructed().getFirst()).close();
                verify(helper, never()).close();
                verify(groups, never()).close();
                verify(images, never()).close();
            }
        });
    }
}
