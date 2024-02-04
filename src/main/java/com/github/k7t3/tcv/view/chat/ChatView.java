package com.github.k7t3.tcv.view.chat;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.vm.chat.ChatDataViewModel;
import com.github.k7t3.tcv.vm.chat.ChatViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import de.saxsys.mvvmfx.utils.viewlist.CachedViewModelCellFactory;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollBar;

import java.net.URL;
import java.util.ResourceBundle;

public class ChatView implements FxmlView<ChatViewModel>, Initializable {

    @FXML
    private Label userNameLabel;

    @FXML
    private Label streamTitleLabel;

    // FIXME テスト用
    @FXML
    private Button cancelButton;

    @FXML
    private ListView<ChatDataViewModel> chatDataList;

    private ScrollBar chatDataScrollBar = null;

    @InjectViewModel
    private ChatViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        userNameLabel.getStyleClass().add(Styles.TEXT_CAPTION);
        userNameLabel.textProperty().bind(viewModel.userNameProperty());

        streamTitleLabel.getStyleClass().add(Styles.TEXT_SMALL);
        streamTitleLabel.textProperty().bind(viewModel.titleProperty());

        chatDataList.setCellFactory(CachedViewModelCellFactory.createForJavaView(ChatDataView.class));
        chatDataList.setItems(viewModel.getChatDataList());

        for (var node : chatDataList.lookupAll(".scroll-bar")) {
            if (node instanceof ScrollBar) {
                chatDataScrollBar = (ScrollBar) node;
                System.out.println("scroll bar found!");
                break;
            }
        }
//        var node = chatDataList.lookup(".scroll-bar");
//        if (node instanceof ScrollBar) {
//            chatDataScrollBar = (ScrollBar) node;
//        }

        viewModel.getChatDataList().addListener((ListChangeListener<? super ChatDataViewModel>) c -> {
            if (chatDataScrollBar == null) {
                return;
            }
            if (0.9 < chatDataScrollBar.getValue() / chatDataScrollBar.getMax()) {
                return;
            }

            while (c.next()) {
                if (c.wasAdded()) {
                    var last = c.getAddedSubList().getLast();
                    chatDataList.scrollTo(last);
                    break;
                }
            }
        });

        // FIXME テスト用
        cancelButton.setOnAction(e -> {
            viewModel.leaveChannelAsync();
            cancelButton.setDisable(true);
        });
    }

}
