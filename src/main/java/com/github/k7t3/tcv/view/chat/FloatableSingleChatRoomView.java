package com.github.k7t3.tcv.view.chat;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.image.LazyImageView;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.FloatableStage;
import com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.effect.SepiaTone;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;

public class FloatableSingleChatRoomView implements FxmlView<SingleChatRoomViewModel>, Initializable {

    private static final double PROFILE_IMAGE_SIZE = 48;

    private LazyImageView profileImageView;

    @FXML
    private HBox headerPane;

    @FXML
    private CheckMenuItem alwaysOnTopMenuItem;

    @FXML
    private CheckMenuItem autoScrollMenuItem;

    @FXML
    private MenuItem restoreMenuItem;

    @FXML
    private MenuItem closeMenuItem;

    @FXML
    private MenuButton menuButton;

    @FXML
    private StackPane chatDataContainer;

    @FXML
    private Pane backgroundImageLayer;

    @FXML
    private Slider opacitySlider;

    @InjectViewModel
    private SingleChatRoomViewModel viewModel;

    private TwitchChannelViewModel channel;

    private VirtualFlow<ChatDataViewModel, ChatDataCell> virtualFlow;

    private FloatableStage floatableStage;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        channel = viewModel.getChannel();

        profileImageView = new LazyImageView();
        headerPane.getChildren().addFirst(profileImageView);
        profileImageView.lazyImageProperty().bind(channel.profileImageProperty());
        profileImageView.setFitWidth(PROFILE_IMAGE_SIZE);
        profileImageView.setFitHeight(PROFILE_IMAGE_SIZE);
        var clip = new Rectangle();
        clip.widthProperty().bind(profileImageView.fitWidthProperty());
        clip.heightProperty().bind(profileImageView.fitHeightProperty());
        clip.arcWidthProperty().bind(profileImageView.fitWidthProperty());
        clip.arcHeightProperty().bind(profileImageView.fitHeightProperty());
        profileImageView.setClip(clip);

        // 配信していないときののイメージを更新する
        updateLiveState();
        channel.liveProperty().addListener((ob, o, n) -> updateLiveState());

        ChatRoomViewUtils.installStreamInfoPopOver(channel, profileImageView);

        floatableStage = new FloatableStage();
        ChatRoomViewUtils.initializeFloatableStage(floatableStage, viewModel);

        var prefs = AppPreferences.getInstance().getChatPreferences();

        menuButton.getStyleClass().addAll(Tweaks.NO_ARROW, Styles.BUTTON_ICON);

        // チャンネルグループに関するメニューを追加
        var repository = AppHelper.getInstance().getChannelGroupRepository();
        menuButton.getItems().addAll(0, List.of(
                new ChannelGroupMenu(repository, FXCollections.observableArrayList(channel)),
                new SeparatorMenuItem()
        ));

        // 閉じるボタン
        closeMenuItem.setOnAction(e -> {
            floatableStage.close();
            viewModel.leaveChatAsync();
        });

        // 元に戻すボタン
        restoreMenuItem.setOnAction(e -> {
            floatableStage.close();
            viewModel.restoreToContainer();
        });

        // チャット
        virtualFlow = VirtualFlow.createVertical(viewModel.getChatDataList(), ChatDataCell::of);
        chatDataContainer.getChildren().add(new VirtualizedScrollPane<>(virtualFlow));

        // 自動スクロールと仮想フローにおける動作を初期化
        ChatRoomViewUtils.initializeVirtualFlowScrollActions(virtualFlow, viewModel.getChatDataList(), viewModel.autoScrollProperty());

        autoScrollMenuItem.selectedProperty().bindBidirectional(viewModel.autoScrollProperty());

        // 透過度
        floatableStage.backgroundOpacityProperty().bindBidirectional(prefs.floatableChatOpacityProperty());
        opacitySlider.valueProperty().bindBidirectional(floatableStage.backgroundOpacityProperty());

        // 常に最前面に表示
        alwaysOnTopMenuItem.selectedProperty().bindBidirectional(prefs.floatableChatAlwaysTopProperty());
        alwaysOnTopMenuItem.selectedProperty().addListener((ob, o, n) -> floatableStage.setAlwaysOnTop(n));
        floatableStage.setAlwaysOnTop(alwaysOnTopMenuItem.isSelected());
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

    public FloatableStage getFloatableStage() {
        return floatableStage;
    }

}
