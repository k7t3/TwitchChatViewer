package com.github.k7t3.tcv.view.command;

import com.github.k7t3.tcv.view.MainViewFactory;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.ToggleSwitch;
import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.SearchChannelViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.TwitchAPI;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import static com.github.k7t3.tcv.app.image.ImageTestDoubles.emptyLoader;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class OpenSearchChannelCommandTest {
    @Test
    void aSearchFailureUsesTheWindowThatOwnsItsModal(FxRobot robot) throws Exception {
        var owner = new AtomicReference<Stage>();
        var modal = new AtomicReference<ModalPane>();
        var command = new AtomicReference<OpenSearchChannelCommand>();
        var alerts = new AtomicReference<MockedConstruction<Alert>>();
        var displayed = new CountDownLatch(1);
        var client = mock(Twitch.class);
        var api = mock(TwitchAPI.class);
        when(client.getTwitchAPI()).thenReturn(api);
        var failure = new IllegalStateException("search unavailable");
        when(api.search("word", false)).thenThrow(failure);
        try (var worker = new TaskWorker()) {
            try {
                robot.interact(() -> {
                    modal.set(new ModalPane());
                    owner.set(new Stage());
                    owner.get().setScene(new Scene(new StackPane(modal.get()), 600, 400));
                    owner.get().show();
                    modal.get().applyCss();
                    var helper = mock(AppHelper.class);
                    var images = emptyLoader();
                    when(helper.getPrimaryStage()).thenReturn(owner.get());
                    when(helper.getExecutor()).thenReturn(worker.getExecutor());
                    when(helper.getImageLoader()).thenReturn(images);
                    when(helper.getSubscribers()).thenReturn(mock(EventSubscribers.class));
                    when(helper.twitchProperty()).thenReturn(new SimpleObjectProperty<Twitch>(client));
                    alerts.set(mockConstruction(Alert.class, (alert, context) -> {
                        when(alert.getDialogPane()).thenReturn(new DialogPane());
                        when(alert.showAndWait()).thenAnswer(call -> {
                            displayed.countDown(); return Optional.empty();
                        });
                    }));
                    command.set(MainViewFactory.search(modal.get(), mock(ChannelViewModelRepository.class), helper,
                            new SimpleBooleanProperty(true)));
                    command.get().execute();

                    ((CustomTextField) modal.get().getContent().lookup("#keywordField")).setText("word");
                    ((ToggleSwitch) modal.get().getContent().lookup("#liveSwitch")).setSelected(false);
                });
                assertThat(displayed.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    assertThat(alerts.get().constructed()).hasSize(1);
                    verify(alerts.get().constructed().getFirst()).initOwner(owner.get());
                    verify(alerts.get().constructed().getFirst()).showAndWait();
                    assertThat(owner.get().isShowing()).isTrue();
                    assertThat(modal.get().isDisplay()).isTrue();
                });
                verify(api).search("word", false);
            } finally {
                robot.interact(() -> {
                    if (command.get() != null) command.get().close();
                    if (owner.get() != null) owner.get().close();
                });
            }
        } finally { robot.interact(() -> { if (alerts.get() != null) alerts.get().close(); }); }
    }

    @Test
    void aShowFailureClosesTheAlreadyLoadedModelAndSessionBinding(FxRobot robot) {
        try (var fixture = new Fixture(robot)) {
            robot.interact(() -> {
                var failure = new IllegalStateException("modal show failed");
                fixture.modal.showFailure = failure;
                assertThatThrownBy(fixture.command::execute).isSameAs(failure);
                assertThat(fixture.models).hasSize(1);
                assertThat(fixture.models.getFirst().isClosed()).isTrue();
                assertThat(fixture.models.getFirst().twitchProperty().isBound()).isFalse();
                assertThat(fixture.models.getFirst().getTwitch()).isNull();
            });
        }
    }

    @Test
    void theOwnerClosesTheVisibleSearchAndCanReplaceItBeforeShutdown(FxRobot robot) {
        try (var fixture = new Fixture(robot)) {
            robot.interact(() -> {
                fixture.command.execute();
                assertThat(fixture.stage.isShowing()).isTrue();
                fixture.command.execute();
                assertThat(fixture.models).hasSize(2);
                assertThat(fixture.models.getFirst().isClosed()).isTrue();
                assertThat(fixture.models.getLast().isClosed()).isFalse();
                fixture.command.close();
                fixture.command.close();
                assertThat(fixture.models.getLast().isClosed()).isTrue();
                assertThat(fixture.modal.getContent()).isNull();
                assertThat(fixture.stage.isShowing()).isTrue();
                assertThatThrownBy(fixture.command::execute).isInstanceOf(IllegalStateException.class);
            });
        }
    }

    @Test
    void hidingTheModalOrLosingAuthorizationReleasesItsSearchImmediately(FxRobot robot) {
        try (var fixture = new Fixture(robot)) {
            robot.interact(() -> {
                fixture.command.execute();
                fixture.modal.hide();
                assertThat(fixture.models.getFirst().isClosed()).isTrue();
                fixture.command.execute();
                fixture.authorized.set(false);
                assertThat(fixture.models.getLast().isClosed()).isTrue();
                fixture.command.execute();
                assertThat(fixture.models).hasSize(2);
                assertThat(fixture.modal.isDisplay()).isFalse();
            });
        }
    }

    @Test
    void replacingTheContentDoesNotLetTheOldSearchHideTheNewModal(FxRobot robot) {
        try (var fixture = new Fixture(robot)) {
            robot.interact(() -> {
                fixture.command.execute();
                var replacement = new Pane();
                fixture.modal.show(replacement);
                assertThat(fixture.models.getFirst().isClosed()).isTrue();
                fixture.command.close();
                assertThat(fixture.modal.getContent()).isSameAs(replacement);
                assertThat(fixture.modal.isDisplay()).isTrue();
            });
        }
    }

    private static final class Fixture implements AutoCloseable {
        final TaskWorker worker = new TaskWorker();
        final EventPublishers publishers = new EventPublishers();
        final ArrayList<SearchChannelViewModel> models = new ArrayList<>();
        final SimpleBooleanProperty authorized = new SimpleBooleanProperty(true);
        final FxRobot robot;
        final FailingModalPane modal;
        final Stage stage;
        final OpenSearchChannelCommand command;

        Fixture(FxRobot robot) {
            this.robot = robot;
            var reference = new AtomicReference<Stage>();
            var modalReference = new AtomicReference<FailingModalPane>();
            robot.interact(() -> {
                var pane = new FailingModalPane();
                var window = new Stage();
                window.setScene(new Scene(new StackPane(pane), 600, 400));
                window.show();
                pane.applyCss();
                reference.set(window);
                modalReference.set(pane);
            });
            stage = reference.get();
            modal = modalReference.get();
            var helper = mock(AppHelper.class);
            when(helper.twitchProperty()).thenReturn(new SimpleObjectProperty<Twitch>(mock(Twitch.class)));
            when(helper.getSubscribers()).thenReturn(new EventSubscribers(publishers));
            command = MainViewFactory.search(modal, helper, authorized, () -> {
                var model = new SearchChannelViewModel(mock(ChannelViewModelRepository.class), emptyLoader(), worker.getExecutor(), unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
                models.add(model);
                return model;
            });
        }

        @Override public void close() {
            try { robot.interact(() -> { command.close(); models.forEach(SearchChannelViewModel::close); stage.close(); }); }
            finally { worker.close(); publishers.close(); }
        }
    }

    private static final class FailingModalPane extends ModalPane {
        RuntimeException showFailure;
        @Override public void show(Node content) {
            if (showFailure != null) throw showFailure;
            super.show(content);
        }
    }
}
