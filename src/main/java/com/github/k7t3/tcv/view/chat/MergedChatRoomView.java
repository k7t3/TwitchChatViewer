package com.github.k7t3.tcv.view.chat;

import atlantafx.base.controls.Popover;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomViewModel;
import com.github.k7t3.tcv.app.chat.MergedChatRoomViewModel;
import com.github.k7t3.tcv.view.core.Resources;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.SepiaTone;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.WindowEvent;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class MergedChatRoomView implements FxmlView<MergedChatRoomViewModel>, Initializable {

    private static final double PROFILE_IMAGE_SIZE = 48;

    @FXML
    private Pane headerPane;

    @FXML
    private ToolBar profileImageContainer;

    @FXML
    private MenuButton actionsMenuButton;

    @FXML
    private CheckMenuItem selectedMenuItem;

    @FXML
    private MenuItem closeMenuItem;

    @FXML
    private MenuItem popoutMenuItem;

    @FXML
    private ToggleButton scrollToEnd;

    @FXML
    private Pane chatRoomControlsContainer;

    @FXML
    private CheckBox selectedCheckBox;

    @FXML
    private StackPane chatDataContainer;

    @FXML
    private Pane backgroundImageLayer;

    private VirtualFlow<ChatDataViewModel, MergedChatDataCell> virtualFlow;

    @InjectViewModel
    private MergedChatRoomViewModel viewModel;

    private Map<TwitchChannelViewModel, Node> profileImageNodes;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        profileImageNodes = new HashMap<>();
        for (var channel : viewModel.getChannels().keySet()) {
            var chatRoom = viewModel.getChannels().get(channel);
            var node = createProfileImageView(channel, chatRoom);
            profileImageNodes.put(channel, node);
            profileImageContainer.getItems().add(node);
        }

        // チャンネルの増減
        viewModel.getChannels().addListener(this::channelChanged);

        actionsMenuButton.getStyleClass().addAll(Styles.FLAT, Tweaks.NO_ARROW);
        closeMenuItem.setOnAction(e -> viewModel.leaveChatAsync());

        // TODO
        popoutMenuItem.setDisable(true);

        virtualFlow = VirtualFlow.createVertical(viewModel.getChatDataList(), MergedChatDataCell::new);
        chatDataContainer.getChildren().add(new VirtualizedScrollPane<>(virtualFlow));

        viewModel.getChatDataList().addListener((ListChangeListener<? super ChatDataViewModel>) c -> {
            if (viewModel.isAutoScroll() && c.next() && c.wasAdded()) {
                virtualFlow.showAsLast(c.getList().size() - 1);
            }
        });

        scrollToEnd.selectedProperty().bindBidirectional(viewModel.autoScrollProperty());

        selectedMenuItem.selectedProperty().bindBidirectional(viewModel.selectedProperty());
        selectedCheckBox.selectedProperty().bindBidirectional(viewModel.selectedProperty());
        viewModel.selectedProperty().addListener((ob, o, n) ->
                headerPane.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), n));
        chatRoomControlsContainer.visibleProperty().bind(viewModel.selectModeProperty().not());
        chatRoomControlsContainer.managedProperty().bind(viewModel.selectModeProperty().not());

        selectedCheckBox.visibleProperty().bind(viewModel.selectModeProperty());
        selectedCheckBox.managedProperty().bind(viewModel.selectModeProperty());
    }

    private Node createProfileImageView(TwitchChannelViewModel channel, ChatRoomViewModel chatRoom) {
        var imageView = new ImageView(channel.getProfileImage());
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(PROFILE_IMAGE_SIZE);
        imageView.setFitHeight(PROFILE_IMAGE_SIZE);
        var clip = new Rectangle();
        clip.widthProperty().bind(imageView.fitWidthProperty());
        clip.heightProperty().bind(imageView.fitHeightProperty());
        clip.arcWidthProperty().bind(imageView.fitWidthProperty());
        clip.arcHeightProperty().bind(imageView.fitHeightProperty());
        imageView.setClip(clip);

        installPopover(channel, imageView);

        channel.liveProperty().addListener((ob, o, n) -> {
            if (n) {
                imageView.setEffect(null);
            } else {
                imageView.setEffect(new SepiaTone());
            }
        });

        var separate = new MenuItem(Resources.getString("chat.separate"), new FontIcon(Feather.EXTERNAL_LINK));
        separate.setOnAction(e -> viewModel.separateChatRoom(chatRoom));

        var close = new MenuItem(Resources.getString("menu.close"), new FontIcon(Feather.X));
        close.setOnAction(e -> viewModel.closeChatRoom(chatRoom));

        var menuButton = new MenuButton(null, imageView, separate, new SeparatorMenuItem(), close);
        menuButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        menuButton.getStyleClass().addAll(Tweaks.NO_ARROW);
        menuButton.setPrefHeight(Region.USE_COMPUTED_SIZE);

        return menuButton;
    }

    private void channelChanged(MapChangeListener.Change<? extends TwitchChannelViewModel, ? extends ChatRoomViewModel> change) {
        if (change.wasAdded()) {
            var channel = change.getKey();
            var chatRoom = change.getValueAdded();
            var node = createProfileImageView(channel, chatRoom);
            profileImageNodes.put(channel, node);
            profileImageContainer.getItems().add(node);
        }
        if (change.wasRemoved()) {
            var channel = change.getKey();
            var node = profileImageNodes.get(channel);
            if (node == null) return;
            profileImageNodes.remove(channel);
            profileImageContainer.getItems().remove(node);
        }
    }

    private void installPopover(TwitchChannelViewModel channel, Node node) {
        var gameNameLabel = new Label();
        gameNameLabel.setWrapText(true);

        var streamTitleLabel = new Label();
        streamTitleLabel.setWrapText(true);

        var viewerCountLabel = new Label();
        viewerCountLabel.setGraphic(new FontIcon(FontAwesomeSolid.USER));
        viewerCountLabel.getStyleClass().add(Styles.DANGER);

        // アップタイムはポップアップを表示したときに計算する
        var uptimeLabel = new Label();
        uptimeLabel.setGraphic(new FontIcon(FontAwesomeSolid.CLOCK));

        var vbox = new VBox(gameNameLabel, streamTitleLabel, viewerCountLabel, uptimeLabel);
        vbox.setPrefWidth(300);
        vbox.setSpacing(4);
        vbox.setPadding(new Insets(10, 0, 10, 0));

        var pop = new Popover(vbox);
        pop.titleProperty().bind(channel.observableUserName());
        pop.setHeaderAlwaysVisible(true);
        pop.setDetachable(false);
        pop.setArrowLocation(Popover.ArrowLocation.TOP_LEFT);

        pop.addEventHandler(WindowEvent.WINDOW_SHOWING, e -> {
            gameNameLabel.setText(channel.getStreamInfo().gameName());
            streamTitleLabel.setText(channel.getStreamInfo().title());
            viewerCountLabel.setText(Integer.toString(channel.getStreamInfo().viewerCount()));

            var now = LocalDateTime.now();
            var startedAt = channel.getStreamInfo().startedAt();
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

        node.setOnMouseEntered(e -> {
            if (channel.isLive()) {
                pop.show(node);
            }
        });
        node.setOnMouseExited(e -> pop.hide());
    }
}