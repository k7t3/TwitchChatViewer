package com.github.k7t3.tcv.view.chat;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.vm.chat.ChatDataViewModel;
import com.github.k7t3.tcv.vm.chat.ChatViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;
import javafx.scene.layout.StackPane;

import java.net.URL;
import java.util.ResourceBundle;

public class ChatView implements FxmlView<ChatViewModel>, Initializable {

    @FXML
    private Label streamTitleLabel;

    @FXML
    private StackPane stateContainer;

    @FXML
    private ToggleSwitch autoScroll;

    @FXML
    private ListView<ChatDataViewModel> chatDataList;

    private ScrollBar chatDataScrollBar = null;

    @InjectViewModel
    private ChatViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        streamTitleLabel.getStyleClass().add(Styles.TEXT_SMALL);
        streamTitleLabel.textProperty().bind(viewModel.titleProperty());

        chatDataList.setCellFactory(param -> new ChatDataListCell());
        chatDataList.setItems(viewModel.getChatDataList());
        chatDataList.getStyleClass().add(Styles.DENSE);

        var roomStateNodes = new ChatRoomStateNodes();
        viewModel.roomStateProperty().addListener((ob, o, n) -> {
            var node = roomStateNodes.getIcon(n);
            if (node == null)
                stateContainer.getChildren().clear();
            else
                stateContainer.getChildren().setAll(node);
        });
        var stateNode = roomStateNodes.getIcon(viewModel.getRoomState());
        if (stateNode != null)
            stateContainer.getChildren().setAll(stateNode);

        viewModel.getChatDataList().addListener((ListChangeListener<? super ChatDataViewModel>) c -> {
            if (chatDataScrollBar == null) {

                var node = chatDataList.lookup(".scroll-bar");
                if (node instanceof ScrollBar) {
                    chatDataScrollBar = (ScrollBar) node;
                }
                if (chatDataScrollBar == null) {
                    return;
                }

            }
            if (viewModel.isScrollToBottom() && c.next() && c.wasAdded()) {
                chatDataScrollBar.setValue(chatDataScrollBar.getMax());
            }
        });

        autoScroll.selectedProperty().bindBidirectional(viewModel.scrollToBottomProperty());
    }

}
