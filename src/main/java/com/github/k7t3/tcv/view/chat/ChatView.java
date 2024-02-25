package com.github.k7t3.tcv.view.chat;

import atlantafx.base.controls.Popover;
import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.chat.ChatViewModel;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.ListChangeListener;
import javafx.collections.SetChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.*;
import javafx.stage.WindowEvent;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ResourceBundle;

public class ChatView implements FxmlView<ChatViewModel>, Initializable {

    @FXML
    private Label userNameLabel;

    @FXML
    private Hyperlink streamInfoLink;

    @FXML
    private MenuButton actionsMenuButton;

    @FXML
    private MenuItem closeMenuItem;

    @FXML
    private MenuItem popoutMenuItem;

    @FXML
    private Pane stateContainer;

    @FXML
    private ToggleSwitch autoScroll;

    @FXML
    private StackPane chatDataContainer;

    @FXML
    private Pane backgroundImageLayer;

    @InjectViewModel
    private ChatViewModel viewModel;

    private VirtualFlow<ChatDataViewModel, ChatDataCell> virtualFlow;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        userNameLabel.textProperty().bind(viewModel.userNameProperty());

        actionsMenuButton.getStyleClass().addAll(Styles.FLAT, Tweaks.NO_ARROW);
        closeMenuItem.setOnAction(e -> viewModel.leaveChatAsync());

        // TODO
        popoutMenuItem.setDisable(true);

        virtualFlow = VirtualFlow.createVertical(viewModel.getChatDataList(), ChatDataCell::new);
        chatDataContainer.getChildren().add(new VirtualizedScrollPane<>(virtualFlow));

        // 配信していないときののイメージを更新する
        updateBackgroundImage();
        viewModel.liveProperty().addListener((ob, o, n) -> updateBackgroundImage());


        var roomStateNodes = new ChatRoomStateNodes();
        viewModel.getRoomStates().addListener((SetChangeListener<? super ChatRoomState>) (c) -> {
            if (c.wasAdded()) {
                var node = roomStateNodes.getIcon(c.getElementAdded());
                stateContainer.getChildren().add(node);
            }
            if (c.wasRemoved()) {
                var node = roomStateNodes.getIcon(c.getElementRemoved());
                stateContainer.getChildren().remove(node);
            }
        });
        viewModel.getRoomStates().stream()
                .map(roomStateNodes::getIcon)
                .forEach(stateContainer.getChildren()::add);


        viewModel.getChatDataList().addListener((ListChangeListener<? super ChatDataViewModel>) c -> {
            if (viewModel.isScrollToBottom() && c.next() && c.wasAdded()) {
                virtualFlow.showAsLast(c.getList().size() - 1);
            }
        });

        autoScroll.selectedProperty().bindBidirectional(viewModel.scrollToBottomProperty());

        installPopover();
        streamInfoLink.visibleProperty().bind(viewModel.liveProperty());
    }

    private void updateBackgroundImage() {
        if (!viewModel.isLive()) {
            var backgroundImage = viewModel.getBackgroundImage();
            if (backgroundImage != null) {
                var bgSize = new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, true, true, true, false);
                var bgImage = new BackgroundImage(backgroundImage, BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT, BackgroundPosition.DEFAULT, bgSize);
                var bg = new Background(bgImage);
                backgroundImageLayer.setBackground(bg);
            }
        }
        backgroundImageLayer.setVisible(!viewModel.isLive());
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

        // アップタイムはポップアップを表示したときに計算する
        var uptimeLabel = new Label();
        uptimeLabel.setGraphic(new FontIcon(FontAwesomeSolid.CLOCK));

        var vbox = new VBox(gameNameLabel, streamTitleLabel, viewerCountLabel, uptimeLabel);
        vbox.setPrefWidth(300);
        vbox.setSpacing(4);
        vbox.setPadding(new Insets(10, 0, 10, 0));

        var pop = new Popover(vbox);
        pop.titleProperty().bind(viewModel.userNameProperty());
        pop.setHeaderAlwaysVisible(true);
        pop.setDetachable(true);
        pop.setArrowLocation(Popover.ArrowLocation.TOP_LEFT);

        pop.addEventHandler(WindowEvent.WINDOW_SHOWING, e -> {
            var now = LocalDateTime.now();
            var startedAt = viewModel.getStartedAt();
            var between = Duration.between(startedAt, now);
            var minutes = between.toMinutes();

            if (minutes < 60) {
                uptimeLabel.setText("%d m".formatted(minutes));
            } else {
                var hours = minutes / 60;
                minutes = minutes - hours * 60;
                uptimeLabel.setText("%d h %d m".formatted(hours, minutes));
            }
        });

        streamInfoLink.setOnAction(e -> pop.show(streamInfoLink));
    }

}
