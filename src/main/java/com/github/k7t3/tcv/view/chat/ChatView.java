package com.github.k7t3.tcv.view.chat;

import atlantafx.base.controls.Popover;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.chat.ChatViewModel;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.util.ResourceBundle;

public class ChatView implements FxmlView<ChatViewModel>, Initializable {

    @FXML
    private Label userNameLabel;

    @FXML
    private Hyperlink streamInfoLink;

    @FXML
    private Button closeButton;

    @FXML
    private Pane stateContainer;

    @FXML
    private ToggleSwitch autoScroll;

    @FXML
    private ListView<ChatDataViewModel> chatDataList;

    private ScrollBar chatDataScrollBar = null;

    @InjectViewModel
    private ChatViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        userNameLabel.textProperty().bind(viewModel.userNameProperty());

        closeButton.getStyleClass().addAll(Styles.DANGER, Styles.BUTTON_OUTLINED, Styles.SMALL);
        closeButton.setOnAction(e -> viewModel.leaveChatAsync());

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

                var node = chatDataList.lookup(".scroll-bar:vertical");
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

        installPopover();
    }

    private void installPopover() {
        var gameNameLabel = new Label();
        gameNameLabel.textProperty().bind(viewModel.gameNameProperty());
        gameNameLabel.setWrapText(true);

        var streamTitleLabel = new Label();
        streamTitleLabel.textProperty().bind(viewModel.titleProperty());
        streamTitleLabel.setWrapText(true);

        var viewerCountLabel = new Label();
        viewerCountLabel.setGraphic(new FontIcon(FontAwesomeSolid.USER));
        viewerCountLabel.getStyleClass().add(Styles.DANGER);
        viewerCountLabel.textProperty().bind(viewModel.viewerCountProperty().asString());

        var vbox = new VBox(gameNameLabel, streamTitleLabel, viewerCountLabel);
        vbox.setPrefWidth(300);
        vbox.setSpacing(4);
        vbox.setPadding(new Insets(10, 0, 10, 0));

        var pop = new Popover(vbox);
        pop.titleProperty().bind(viewModel.userNameProperty());
        pop.setHeaderAlwaysVisible(true);
        pop.setDetachable(true);
        pop.setArrowLocation(Popover.ArrowLocation.RIGHT_TOP);

        streamInfoLink.setOnAction(e -> pop.show(streamInfoLink));
    }

}
