package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.view.core.BasicPopup;
import javafx.stage.WindowEvent;

public class LiveInfoPopup extends BasicPopup {

    public LiveInfoPopup(TwitchChannelViewModel channel) {
        var view = new LiveInfoView(channel);
        setContent(view);
        setTransparency(0.9);
        addEventHandler(WindowEvent.WINDOW_SHOWING, e -> view.computeUptimeLabel());
    }
}
