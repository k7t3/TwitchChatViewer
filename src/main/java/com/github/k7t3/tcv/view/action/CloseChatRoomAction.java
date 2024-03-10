package com.github.k7t3.tcv.view.action;

import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

public class CloseChatRoomAction extends AbstractKeyAction {

    private final ChatRoomContainerViewModel containerViewModel;

    public CloseChatRoomAction(ChatRoomContainerViewModel containerViewModel) {
        super(new KeyCodeCombination(KeyCode.W, KeyCombination.SHORTCUT_DOWN));
        this.containerViewModel = containerViewModel;
    }

    @Override
    public void run() {
        containerViewModel.removeLast();
    }

}
