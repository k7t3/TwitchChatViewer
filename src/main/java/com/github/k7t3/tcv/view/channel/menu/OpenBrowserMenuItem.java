package com.github.k7t3.tcv.view.channel.menu;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.service.FXTask;
import javafx.event.ActionEvent;
import javafx.scene.control.MenuItem;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

public class OpenBrowserMenuItem extends MenuItem  {

    private final TwitchChannelViewModel channel;

    public OpenBrowserMenuItem(TwitchChannelViewModel channel) {
        super(Resources.getString("channel.open.browser"), new FontIcon(FontAwesomeSolid.GLOBE));
        this.channel = channel;
        addEventHandler(ActionEvent.ACTION, this::action);
    }

    private void action(ActionEvent e) {
        e.consume();
        FXTask.task(channel::openChannelPageOnBrowser).runAsync();
    }
}
