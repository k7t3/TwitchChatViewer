package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

import java.util.stream.Collectors;

@SuppressWarnings("ALL")
public class ChatDataContextMenu extends ContextMenu {

    private final ChatDataViewModel viewModel;

    public ChatDataContextMenu(ChatDataViewModel viewModel) {
        this.viewModel = viewModel;

        setHideOnEscape(true);

        // TODO

        var copy = new MenuItem("Copy Text");
        copy.setOnAction(e -> viewModel.copyMessage());

        var addToNG = new MenuItem("Add to NG");
        addToNG.setDisable(true);

        getItems().addAll(copy, addToNG);
    }

}
