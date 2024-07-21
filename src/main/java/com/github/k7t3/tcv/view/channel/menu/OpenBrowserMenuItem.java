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
