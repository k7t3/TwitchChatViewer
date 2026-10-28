package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelBorrowedLifecycleTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void endingTheViewDetachesTheBorrowedDomainWithoutClosingItOnFx(boolean logout, FxRobot robot) {
        var domain = mock(ChannelRepository.class);
        doThrow(new AssertionError("the runtime owns domain cleanup")).when(domain).clear();
        var model = new ChannelViewModelRepository(mock(ChannelGroupRepository.class), mock(ImageLoader.class), Runnable::run, ignoredShutdown -> {}, unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        try {
            robot.interact(() -> {
                model.setRepository(domain);

                assertThatCode(() -> { if (logout) model.onLogout(); else model.close(); }).doesNotThrowAnyException();

                assertThat(model.getRepository()).isNull();
                verify(domain, never()).clear();
            });
        } finally {
            robot.interact(model::close);
        }
    }
}
