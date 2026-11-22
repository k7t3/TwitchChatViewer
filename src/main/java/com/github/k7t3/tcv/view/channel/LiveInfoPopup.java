/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.view.core.BasicPopup;
import javafx.stage.WindowEvent;

public class LiveInfoPopup extends BasicPopup implements AutoCloseable {
    private final LiveInfoView view;
    private final javafx.event.EventHandler<WindowEvent> showing;
    private boolean closed;

    public LiveInfoPopup(TwitchChannelViewModel channel) {
        view = new LiveInfoView(channel);
        setContent(view);
        setTransparency(0.9);
        showing = event -> { if (!closed) view.computeUptimeLabel(); };
        addEventHandler(WindowEvent.WINDOW_SHOWING, showing);
    }
    @Override public void close() {
        if (closed) return;
        closed = true;
        com.github.k7t3.tcv.lifecycle.CloseActions.runAll(
                () -> removeEventHandler(WindowEvent.WINDOW_SHOWING, showing), this::hide,
                () -> view.setChannel(null), () -> setSkin(null), () -> setContent(null));
    }

}
