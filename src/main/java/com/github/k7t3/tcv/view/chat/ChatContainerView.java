package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.app.chat.ChatViewModel;
import com.github.k7t3.tcv.view.core.Resources;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.*;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class ChatContainerView implements FxmlView<ChatContainerViewModel>, Initializable {

    @FXML
    private BorderPane container;

    private GridPane chatContainer;

    @InjectViewModel
    private ChatContainerViewModel viewModel;

    private Map<ChatViewModel, Node> items;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        items = new HashMap<>();
        viewModel.getChatList().addListener(this::chatChanged);

        chatContainer = new GridPane();
        container.setCenter(chatContainer);
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
        var node = items.get(chat);
        if (node == null) return;

        chatContainer.getChildren().remove(node);
    }

    private void onAdded(ChatViewModel chat) {
        var tuple = FluentViewLoader.fxmlView(ChatView.class)
                .viewModel(chat)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var node = tuple.getView();

        var columnCount = chatContainer.getColumnCount();
        GridPane.setFillWidth(node, true);
        GridPane.setFillHeight(node, true);
        GridPane.setHgrow(node, Priority.ALWAYS);
        GridPane.setVgrow(node, Priority.ALWAYS);
        chatContainer.addColumn(columnCount, node);

        tuple.getViewModel().joinChatAsync();

        items.put(chat, node);
    }

}
