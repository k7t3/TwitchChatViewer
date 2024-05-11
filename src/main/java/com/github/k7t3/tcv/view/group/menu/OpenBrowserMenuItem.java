package com.github.k7t3.tcv.view.group.menu;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import javafx.event.ActionEvent;
import javafx.scene.control.MenuItem;

public class OpenBrowserMenuItem extends MenuItem  {

    private final TwitchChannelViewModel channel;

    public OpenBrowserMenuItem(TwitchChannelViewModel channel) {
        super(Resources.getString("group.channel.open.browser"));
        this.channel = channel;
        addEventHandler(ActionEvent.ACTION, this::action);
    }

    private void action(ActionEvent e) {
        e.consume();
        channel.openChannelPageOnBrowser();
    }
}
