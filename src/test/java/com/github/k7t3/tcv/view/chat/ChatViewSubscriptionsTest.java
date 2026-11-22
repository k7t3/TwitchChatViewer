package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.view.channel.LiveInfoPopup;
import com.github.k7t3.tcv.view.channel.LiveInfoView;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.fxmisc.flowless.Cell;
import org.fxmisc.flowless.VirtualFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChatViewSubscriptionsTest {
    @Test
    void endingScrollActionsDetachesTheOldMessagesAndScrollHandler(FxRobot robot) {
        robot.interact(() -> {
            var items = FXCollections.<String>observableArrayList();
            var flow = spy(VirtualFlow.createVertical(items, text -> new Cell<String, Label>() {
                private final Label node = new Label(text);
                @Override public Label getNode() { return node; }
            }));
            var autoScroll = new SimpleBooleanProperty(true);
            var actions = ChatRoomViewUtils.initializeVirtualFlowScrollActions(flow, items, autoScroll);
            try {
                items.add("first"); verify(flow).showAsLast(0);
                flow.fireEvent(scrollUp()); assertThat(autoScroll.get()).isFalse();
                actions.unsubscribe(); actions.unsubscribe(); clearInvocations(flow);
                autoScroll.set(true); flow.fireEvent(scrollUp()); assertThat(autoScroll.get()).isTrue();
                items.add("after close"); verify(flow, never()).showAsLast(anyInt());
            } finally { actions.unsubscribe(); flow.dispose(); }
        });
    }

    @Test
    void endingTheHoverSubscriptionClosesAndDetachesItsLiveInfoPopup(FxRobot robot) {
        robot.interact(() -> {
            var channel = mock(TwitchChannelViewModel.class, RETURNS_DEEP_STUBS);
            when(channel.isLive()).thenReturn(true);
            when(channel.getStreamInfo().startedAt()).thenReturn(LocalDateTime.now());
            var node = new Label("Channel"); var window = new Stage();
            window.setScene(new Scene(new StackPane(node), 400, 240)); window.show();
            var actions = ChatRoomViewUtils.installStreamInfoPopOver(channel, node);
            try {
                node.fireEvent(moved());
                var popup = Window.getWindows().stream().filter(LiveInfoPopup.class::isInstance).map(LiveInfoPopup.class::cast).findFirst().orElseThrow();
                var content = (LiveInfoView) popup.getContent(); assertThat(content.getChannel()).isSameAs(channel);
                actions.unsubscribe(); actions.unsubscribe();
                assertThat(popup.isShowing()).isFalse(); assertThat(content.getChannel()).isNull();
                node.fireEvent(moved()); assertThat(popup.isShowing()).isFalse();
            } finally { actions.unsubscribe(); window.close(); }
        });
    }

    private static ScrollEvent scrollUp() {
        return new ScrollEvent(ScrollEvent.SCROLL, 0, 0, 0, 0, false, false, false, false, false, false,
                0, 10, 0, 10, ScrollEvent.HorizontalTextScrollUnits.NONE, 0, ScrollEvent.VerticalTextScrollUnits.NONE, 0, 0, null);
    }
    private static MouseEvent moved() {
        return new MouseEvent(MouseEvent.MOUSE_MOVED, 0, 0, 0, 0, MouseButton.NONE, 0,
                false, false, false, false, false, false, false, false, false, false, null);
    }
}
