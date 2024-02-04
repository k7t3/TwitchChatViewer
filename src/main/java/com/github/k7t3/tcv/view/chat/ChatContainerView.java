package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.vm.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.vm.chat.ChatViewModel;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;

public class ChatContainerView implements FxmlView<ChatContainerViewModel>, Initializable {

    @FXML
    private StackPane container;

    @InjectViewModel
    private ChatContainerViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        viewModel.getChatList().addListener(this::chatChanged);
    }

    private void chatChanged(ListChangeListener.Change<? extends ChatViewModel> c) {
        while (c.next()) {
            if (c.wasAdded()) {
                for (var chat : c.getAddedSubList())
                    addChat(chat);
            }
            if (c.wasRemoved()) {
                // TODO
            }
        }
    }

    private void addChat(ChatViewModel chat) {
        var tuple = FluentViewLoader.fxmlView(ChatView.class)
                .viewModel(chat)
                .resourceBundle(Resources.getResourceBundle())
                .load();
        container.getChildren().add(tuple.getView());
        tuple.getViewModel().joinChannelAsync();
    }

}
