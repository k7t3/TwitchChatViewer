package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import com.github.k7t3.tcv.view.core.Resources;
import javafx.scene.Node;
import javafx.scene.control.Tooltip;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.HashMap;
import java.util.Map;

public class ChatRoomStateNodes {

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
        applyTooltip(state, icon);
        return icon;
    }

    private void applyTooltip(ChatRoomState state, Node node) {
        switch (state) {
            case EMOTE_ONLY -> Tooltip.install(node, new Tooltip(Resources.getString("chat.state.emote_only")));
            case FOLLOWERS_ONLY -> Tooltip.install(node, new Tooltip(Resources.getString("chat.state.followers_only")));
            case SLOW_MODE -> Tooltip.install(node, new Tooltip(Resources.getString("chat.state.slow_mode")));
            case SUBSCRIBERS_ONLY -> Tooltip.install(node, new Tooltip(Resources.getString("chat.state.subscribers_only")));
        }
    }

}
