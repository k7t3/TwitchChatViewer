package com.github.k7t3.tcv.view.chat;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel;
import com.github.k7t3.tcv.domain.chat.ChatRoomState;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.SetChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.effect.SepiaTone;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.net.URL;
import java.util.ResourceBundle;

public class SingleChatRoomView implements FxmlView<SingleChatRoomViewModel>, Initializable {

    private static final double PROFILE_IMAGE_SIZE = 48;

    @FXML
    private Pane headerPane;

    @FXML
    private ImageView profileImageView;

    @FXML
    private Label userNameLabel;

    @FXML
    private Label streamTitleLabel;

    @FXML
    private MenuButton actionsMenuButton;

    @FXML
    private CheckMenuItem selectedMenuItem;

    @FXML
    private MenuItem closeMenuItem;

    @FXML
    private MenuItem popoutMenuItem;

    @FXML
    private Pane chatRoomControlsContainer;

    @FXML
    private CheckBox selectedCheckBox;

    @FXML
    private Pane stateContainer;

    @FXML
    private ToggleButton scrollToEnd;

    @FXML
    private StackPane chatDataContainer;

    @FXML
    private Pane backgroundImageLayer;

    private VirtualFlow<ChatDataViewModel, ChatDataCell> virtualFlow;

    @InjectViewModel
    private SingleChatRoomViewModel viewModel;

    private TwitchChannelViewModel channel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        channel = viewModel.getChannel();

        profileImageView.imageProperty().bind(channel.profileImageProperty());
        profileImageView.setFitWidth(PROFILE_IMAGE_SIZE);
        profileImageView.setFitHeight(PROFILE_IMAGE_SIZE);
        var clip = new Rectangle();
        clip.widthProperty().bind(profileImageView.fitWidthProperty());
        clip.heightProperty().bind(profileImageView.fitHeightProperty());
        clip.arcWidthProperty().bind(profileImageView.fitWidthProperty());
        clip.arcHeightProperty().bind(profileImageView.fitHeightProperty());
        profileImageView.setClip(clip);

        userNameLabel.textProperty().bind(channel.observableUserName());

        actionsMenuButton.getStyleClass().addAll(Styles.FLAT, Tweaks.NO_ARROW);
        closeMenuItem.setOnAction(e -> viewModel.leaveChatAsync());

        popoutMenuItem.setOnAction(e -> viewModel.popOutAsFloatableStage());

        virtualFlow = VirtualFlow.createVertical(viewModel.getChatDataList(), ChatDataCell::of);
        chatDataContainer.getChildren().add(new VirtualizedScrollPane<>(virtualFlow));

        // 配信していないときののイメージを更新する
        updateLiveState();
        channel.liveProperty().addListener((ob, o, n) -> updateLiveState());


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

        // 自動スクロールと仮想フローにおける動作を初期化
        ChatRoomViewUtils.initializeVirtualFlowScrollActions(virtualFlow, viewModel.getChatDataList(), viewModel.autoScrollProperty());

        scrollToEnd.selectedProperty().bindBidirectional(viewModel.autoScrollProperty());

        ChatRoomViewUtils.installStreamInfoPopOver(channel, profileImageView);

        streamTitleLabel.visibleProperty().bind(channel.liveProperty());
        streamTitleLabel.textProperty().bind(channel.observableTitle());
        streamTitleLabel.getStyleClass().addAll(Styles.TEXT_MUTED, Styles.TEXT_SMALL);

        var titleTooltip = new Tooltip();
        titleTooltip.textProperty().bind(streamTitleLabel.textProperty());
        streamTitleLabel.setTooltip(titleTooltip);

        // ヘッダをダブルクリックすると選択状態を切り替える
        headerPane.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                e.consume();
                viewModel.setSelected(!viewModel.isSelected());
            }
        });

        selectedMenuItem.selectedProperty().bindBidirectional(viewModel.selectedProperty());
        selectedCheckBox.selectedProperty().bindBidirectional(viewModel.selectedProperty());
        viewModel.selectedProperty().addListener((ob, o, n) ->
                headerPane.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), n));
        chatRoomControlsContainer.visibleProperty().bind(viewModel.selectModeProperty().not());
        chatRoomControlsContainer.managedProperty().bind(viewModel.selectModeProperty().not());

        selectedCheckBox.visibleProperty().bind(viewModel.selectModeProperty());
        selectedCheckBox.managedProperty().bind(viewModel.selectModeProperty());
    }

    private void updateLiveState() {
        if (!channel.isLive()) {
            var backgroundImage = channel.getOfflineImage();
            if (backgroundImage != null) {
                var bgSize = new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, true, true, true, false);
                var bgImage = new BackgroundImage(backgroundImage, BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT, BackgroundPosition.DEFAULT, bgSize);
                var bg = new Background(bgImage);
                backgroundImageLayer.setBackground(bg);
            }
            profileImageView.setEffect(new SepiaTone());
        } else {
            backgroundImageLayer.setBackground(null);
            profileImageView.setEffect(null);
        }

        backgroundImageLayer.setVisible(!channel.isLive());
    }

}
