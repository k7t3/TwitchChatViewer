package com.github.k7t3.tcv.view.channel.menu;

import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.event.EventBus;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.MenuItem;
import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;

public class OpenChatMenuItem extends MenuItem {

    private final MultipleChatOpenType openType;

    private final List<TwitchChannelViewModel> channels;

    public OpenChatMenuItem(TwitchChannelViewModel channel) {
        this(MultipleChatOpenType.SEPARATED, List.of(channel));
    }

    public OpenChatMenuItem(MultipleChatOpenType openType, List<TwitchChannelViewModel> channels) {
        super(Resources.getString("channel.open.chat"), new FontIcon(FontAwesomeRegular.COMMENT_DOTS));
        this.openType = openType;
        this.channels = channels;
        addEventHandler(ActionEvent.ACTION, this::action);
    }

    private void action(ActionEvent e) {
        e.consume();

        var opening = new ChatOpeningEvent(openType, channels);
        var eventBus = EventBus.getInstance();
        eventBus.publish(opening);
    }
}
