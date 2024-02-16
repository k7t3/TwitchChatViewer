package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.app.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.app.chat.ChatViewModel;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class ChatContainerView implements FxmlView<ChatContainerViewModel>, Initializable {

    @FXML
    private TabPane container;

    @InjectViewModel
    private ChatContainerViewModel viewModel;

    private Map<ChatViewModel, Tab> tabs;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        tabs = new HashMap<>();
        viewModel.getChatList().addListener(this::chatChanged);
    }

    private void chatChanged(ListChangeListener.Change<? extends ChatViewModel> c) {
        while (c.next()) {
            if (c.wasAdded()) {
                for (var chat : c.getAddedSubList())
                    onAdded(chat);
            }
            if (c.wasRemoved()) {
                for (var chat : c.getRemoved())
                    onRemoved(chat);
            }
        }
    }

    private void onRemoved(ChatViewModel chat) {
        var tab = tabs.get(chat);
        if (tab == null) return;

        container.getTabs().remove(tab);
    }

    private void onAdded(ChatViewModel chat) {
        var tuple = FluentViewLoader.fxmlView(ChatView.class)
                .viewModel(chat)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var tab = new Tab("", tuple.getView());
        tab.textProperty().bind(chat.userNameProperty());
        tab.setOnCloseRequest(e -> {
            chat.leaveChatAsync();
            tab.setDisable(true);
        });

        container.getTabs().add(tab);
        tuple.getViewModel().joinChatAsync();

        tabs.put(chat, tab);
    }

}
