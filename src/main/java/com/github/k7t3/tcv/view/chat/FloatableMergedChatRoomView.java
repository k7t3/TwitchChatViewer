package com.github.k7t3.tcv.view.chat;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.chat.MergedChatRoomViewModel;
import com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.view.image.LazyImageView;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.FloatableStage;
import com.github.k7t3.tcv.view.group.menu.ChannelGroupMenu;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.SepiaTone;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class FloatableMergedChatRoomView implements FxmlView<MergedChatRoomViewModel>, Initializable {

    private static final double PROFILE_IMAGE_SIZE = 48;

    @FXML
    private ToolBar profileImageContainer;

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
    private MergedChatRoomViewModel viewModel;

    private VirtualFlow<ChatDataViewModel, ChatDataCell> virtualFlow;

    private FloatableStage floatableStage;

    private Map<TwitchChannelViewModel, Node> profileImageNodes;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

        floatableStage = new FloatableStage();
        ChatRoomViewUtils.initializeFloatableStage(floatableStage, viewModel);

        var prefs = AppPreferences.getInstance().getChatPreferences();

        menuButton.getStyleClass().addAll(Tweaks.NO_ARROW, Styles.BUTTON_ICON);

        // チャンネルグループに関するメニュー
        var repository = AppHelper.getInstance().getChannelGroupRepository();
        menuButton.getItems().addAll(0, List.of(
                new ChannelGroupMenu(repository, FXCollections.observableArrayList(viewModel.getChannels().keySet())),
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
        virtualFlow = VirtualFlow.createVertical(viewModel.getChatDataList(), ChatDataCell::merged);
        chatDataContainer.getChildren().add(new VirtualizedScrollPane<>(virtualFlow));

        // 自動スクロールと仮想フローにおける動作を初期化
        ChatRoomViewUtils.initializeVirtualFlowScrollActions(virtualFlow, viewModel.getChatDataList(), viewModel.autoScrollProperty());

        autoScrollMenuItem.selectedProperty().bindBidirectional(viewModel.autoScrollProperty());

        // 透過度
        floatableStage.backgroundOpacityProperty().bindBidirectional(prefs.floatingChatOpacityProperty());
        opacitySlider.valueProperty().bindBidirectional(floatableStage.backgroundOpacityProperty());

        // 常に最前面に表示
        alwaysOnTopMenuItem.selectedProperty().bindBidirectional(prefs.floatingChatAlwaysTopProperty());
        alwaysOnTopMenuItem.selectedProperty().addListener((ob, o, n) -> floatableStage.setAlwaysOnTop(n));
        floatableStage.setAlwaysOnTop(alwaysOnTopMenuItem.isSelected());

        profileImageNodes = new HashMap<>();
        for (var channel : viewModel.getChannels().keySet()) {
            var node = createProfileImageView(channel);
            profileImageNodes.put(channel, node);
            profileImageContainer.getItems().add(node);
        }
        viewModel.getChannels().addListener(this::channelChanged);
    }

    public FloatableStage getFloatableStage() {
        return floatableStage;
    }

    /**
     * ブロードキャスターのプロファイルイメージNode
     */
    private Node createProfileImageView(TwitchChannelViewModel channel) {
        var imageView = new LazyImageView(channel.getProfileImage());
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

        ChatRoomViewUtils.installStreamInfoPopOver(channel, imageView);

        if (channel.isLive()) {
            imageView.setEffect(null);
        } else {
            imageView.setEffect(new SepiaTone());
        }

        channel.liveProperty().addListener((ob, o, n) -> {
            if (n) {
                imageView.setEffect(null);
            } else {
                imageView.setEffect(new SepiaTone());
            }
        });

        return imageView;
    }

    /**
     * 監視するチャンネルの一覧が更新されたときにViewに反映するメソッド
     */
    private void channelChanged(MapChangeListener.Change<? extends TwitchChannelViewModel, ? extends SingleChatRoomViewModel> change) {
        if (change.wasAdded()) {
            var channel = change.getKey();
            var node = createProfileImageView(channel);
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

}
