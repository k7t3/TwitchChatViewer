package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.SearchChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.event.AppEvent;
import com.github.k7t3.tcv.app.service.TaskWorker;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.MvvmFX;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static com.github.k7t3.tcv.app.image.ImageTestDoubles.emptyLoader;
import static org.mockito.Mockito.mock;

@ExtendWith(ApplicationExtension.class)
class SearchChannelViewLifecycleTest {

    @Test
    void removingTheViewUnbindsItsControlsAndLayout(FxRobot robot) {
        var worker = new TaskWorker();
        var model = new SearchChannelViewModel(mock(ChannelViewModelRepository.class), emptyLoader(), worker.getExecutor(), unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        try {
            robot.interact(() -> {
                model.setKeyword("before");
                var tuple = FluentViewLoader.fxmlView(SearchChannelView.class).codeBehind(new SearchChannelView())
                        .viewModel(model).resourceBundle(Resources.getResourceBundle()).load();
                var view = (javafx.scene.layout.Pane) tuple.getView();
                var field = tuple.getCodeBehind().getKeywordField();
                var container = new StackPane(view);
                container.getChildren().clear();
                model.setKeyword("after close");
                assertThat(field.getText()).isEqualTo("before");
                assertThat(view.prefWidthProperty().isBound()).isFalse();
                assertThat(view.prefHeightProperty().isBound()).isFalse();
                field.setText("detached");
                assertThat(model.getKeyword()).isEqualTo("after close");
            });
        } finally {
            try { robot.interact(model::close); }
            finally { worker.close(); }
        }
    }

    @Test
    void removingSearchViewClosesItsViewModel(FxRobot robot) {
        var worker = new TaskWorker();
        var model = new SearchChannelViewModel(mock(ChannelViewModelRepository.class), emptyLoader(), worker.getExecutor(), unexpectedModelFailure -> { throw new AssertionError(unexpectedModelFailure); });
        var received = new ArrayList<ProbeEvent>();
        try {
            robot.interact(() -> {
                var view = FluentViewLoader.fxmlView(SearchChannelView.class).codeBehind(new SearchChannelView())
                        .viewModel(model).resourceBundle(Resources.getResourceBundle()).load().getView();
                model.subscribe(ProbeEvent.class, received::add);
                var container = new StackPane(view);

                container.getChildren().clear();
                MvvmFX.getNotificationCenter().publish(ProbeEvent.class.getName(), new ProbeEvent());
            });

            robot.interact(() -> assertThat(received).isEmpty());
        } finally {
            try { robot.interact(model::close); }
            finally { worker.close(); }
        }
    }

    private record ProbeEvent() implements AppEvent {
    }
}
