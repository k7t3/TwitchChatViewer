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

package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.app.core.Resources;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;

public class ChatRoomStateNodes {

    private static final String STYLE_CLASS_LABEL = "chat-room-state-label";
    private static final String STYLE_CLASS = "chat-room-state";

    private final Map<ChatRoomState, Node> stateNodes = new HashMap<>();

    public ChatRoomStateNodes() {
    }

    public Node getIcon(ChatRoomState state) {
        if (state == ChatRoomState.NORMAL) {
            return null;
        }
        return stateNodes.computeIfAbsent(state, this::loadIcon);
    }
    
    private Node loadIcon(ChatRoomState state) {
        var icon = switch (state) {
            case EMOTE_ONLY -> new FontIcon(FontAwesomeSolid.LAUGH_BEAM);
            case FOLLOWERS_ONLY -> new FontIcon(FontAwesomeSolid.STAR);
            case ROBOT_9000 -> new FontIcon(FontAwesomeSolid.ROBOT);
            case SLOW_MODE -> new FontIcon(FontAwesomeSolid.WALKING);
            case SUBSCRIBERS_ONLY -> new FontIcon(FontAwesomeSolid.NEWSPAPER);
            default -> throw new IllegalArgumentException();
        };
        icon.getStyleClass().add(STYLE_CLASS);

        var label = new Label("", icon);
        label.getStyleClass().addAll(STYLE_CLASS_LABEL);
        label.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);

        applyText(state, label);

        return label;
    }

    private void applyText(ChatRoomState state, Label label) {
        var text = switch (state) {
            case EMOTE_ONLY -> Resources.getString("chat.state.emote_only");
            case FOLLOWERS_ONLY -> Resources.getString("chat.state.followers_only");
            case SLOW_MODE -> Resources.getString("chat.state.slow_mode");
            case SUBSCRIBERS_ONLY -> Resources.getString("chat.state.subscribers_only");
            default -> "";
        };

        label.setTooltip(new Tooltip(text));
    }

}
