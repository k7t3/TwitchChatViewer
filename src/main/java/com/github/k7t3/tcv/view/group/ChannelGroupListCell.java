package com.github.k7t3.tcv.view.group;

import atlantafx.base.controls.Card;
import atlantafx.base.controls.Tile;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupListViewModel;
import com.github.k7t3.tcv.app.image.LazyImageView;
import com.github.k7t3.tcv.prefs.GeneralPreferences;
import com.github.k7t3.tcv.view.channel.LiveInfoPopup;
import com.github.k7t3.tcv.view.channel.menu.OpenBrowserMenuItem;
import com.github.k7t3.tcv.view.channel.menu.OpenChatMenuItem;
import com.github.k7t3.tcv.view.control.EditableLabel;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.SepiaTone;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.fxmisc.flowless.Cell;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.fontawesome5.FontAwesomeRegular;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Comparator;

public class ChannelGroupListCell extends Card implements Cell<ChannelGroup, Region> {

    private static final String STYLE_CLASS = "channel-group-cell";
    private static final String TILE_STYLE_CLASS = "channel-group-tile";

    private static final double PROFILE_IMAGE_WIDTH = 48;
    private static final double PROFILE_IMAGE_HEIGHT = 48;

    private final EditableLabel header = new EditableLabel();
    private final EditableLabel subHeader = new EditableLabel();
    private final TilePane tilePane = new TilePane();

    private final GeneralPreferences generalPrefs;
    private final ChannelGroup group;
    private final ChannelGroupListViewModel viewModel;

    public ChannelGroupListCell(
            GeneralPreferences generalPrefs,
            ChannelGroup group,
            ChannelGroupListViewModel viewModel
    ) {
        this.generalPrefs = generalPrefs;
        this.group = group;
        this.viewModel = viewModel;
        init();
        update();
    }

    private void init() {
        getStyleClass().add(STYLE_CLASS);
        setHeader(header);
        setSubHeader(subHeader);
        setBody(tilePane);

        header.getStyleClass().add(Styles.TITLE_3);
        header.setTooltip(new Tooltip(Resources.getString("group.tooltip.title")));
        header.getStyleClass().add("title");
        subHeader.setTooltip(new Tooltip(Resources.getString("group.tooltip.comment")));
        subHeader.getStyleClass().add("comment");

        // ****************************************
        // チャットを開くボタン
        // ****************************************
        var openSeparatedItem = new MenuItem(Resources.getString("group.button.open.separated.chat"));
        openSeparatedItem.setOnAction(e -> {
            e.consume();

            var openType = MultipleChatOpenType.SEPARATED;
            var opening = new ChatOpeningEvent(openType, group.getChannels().stream().filter(TwitchChannelViewModel::isLive).toList());
            viewModel.publish(opening);
        });
        var openMergedItem = new MenuItem(Resources.getString("group.button.open.merged.chat"));
        openMergedItem.setOnAction(e -> {
            e.consume();

            var openType = MultipleChatOpenType.MERGED;
            var opening = new ChatOpeningEvent(openType, group.getChannels().stream().filter(TwitchChannelViewModel::isLive).toList());
            viewModel.publish(opening);
        });
        var openChatButton = new SplitMenuButton(openSeparatedItem, openMergedItem);
        openChatButton.setText(Resources.getString("group.button.open.chat"));
        openChatButton.setGraphic(new FontIcon(FontAwesomeRegular.COMMENT_DOTS));
        openChatButton.getStyleClass().add(Styles.ACCENT);
        openChatButton.setOnAction(e -> {
            e.consume();

            var openType = generalPrefs.getMultipleOpenType();
            var opening = new ChatOpeningEvent(openType, group.getChannels().stream().filter(TwitchChannelViewModel::isLive).toList());
            viewModel.publish(opening);
        });

        // ****************************************
        // 削除ボタン
        // ****************************************
        var deleteButton = new Button(Resources.getString("group.button.delete"), new FontIcon(Feather.TRASH));
        deleteButton.getStyleClass().addAll(Styles.DANGER);
        deleteButton.setOnAction(e -> {
            e.consume();

            var alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.initOwner(getScene().getWindow());
            alert.setTitle(Resources.getString("group.dialog.delete.title"));
            alert.setHeaderText(Resources.getString("group.dialog.delete.header"));
            alert.setContentText(Resources.getString("group.dialog.delete.content").formatted(group.getName()));
            alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            var result = alert.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.YES) {
                return;
            }

            setDisable(true);
            var t = viewModel.delete(group);
            t.setFinally(() -> setDisable(false));
        });

        var footer = new HBox(openChatButton, deleteButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setSpacing(4);
        setFooter(footer);

        header.setOnEditCommit(e -> {
            setDisable(true);
            var t = viewModel.update(group);
            t.setFinally(() -> setDisable(false));
        });
        subHeader.setOnEditCommit(e -> {
            setDisable(true);
            var t = viewModel.update(group);
            t.setFinally(() -> setDisable(false));
        });
    }

    @Override
    public Region getNode() {
        return this;
    }

    @Override
    public void dispose() {
        header.textProperty().unbindBidirectional(group.nameProperty());
        subHeader.textProperty().unbindBidirectional(group.commentProperty());
    }

    public void update() {
        header.textProperty().bindBidirectional(group.nameProperty());
        subHeader.textProperty().bindBidirectional(group.commentProperty());
        tilePane.getChildren().clear();
        tilePane.setHgap(4);
        tilePane.setVgap(2);

        group.getChannels().stream()
                .sorted(Comparator.comparing(TwitchChannelViewModel::getUserName))
                .sorted((c1, c2) -> Boolean.compare(c2.isLive(), c1.isLive()))
                .map(this::createChannelNode)
                .forEach(tilePane.getChildren()::add);
    }

    private Node createChannelNode(TwitchChannelViewModel channel) {
        var tile = new Tile();
        tile.getStyleClass().add(TILE_STYLE_CLASS);
        tile.setPrefWidth(USE_COMPUTED_SIZE);
        tile.titleProperty().bind(channel.liveProperty().map(live -> live ? "LIVE" : null));
        tile.descriptionProperty().bind(channel.observableUserName());

        // ツールチップに配信のタイトルを割り当て
        var tooltip = new Tooltip();
        tooltip.textProperty().bind(channel.observableStreamTitle());

        // チャンネルページをブラウザで開く
        var openPageMenuItem = new OpenBrowserMenuItem(channel);

        // グループから削除
        var removeMenuItem = new MenuItem(Resources.getString("group.button.remove"), new FontIcon(Feather.X));
        removeMenuItem.setOnAction(e -> {
            group.getChannels().remove(channel);
            var repository = AppHelper.getInstance().getChannelGroupRepository();
            setDisable(true);
            var t = repository.saveAsync(group);
            t.onDone(() -> tilePane.getChildren().remove(tile));
            t.setFinally(() -> setDisable(false));
        });

        // チャットを開く
        var openChatMenuItem = new OpenChatMenuItem(channel);

        // タイルのアクション(MenuButton)
        var menuButton = new MenuButton(null, new FontIcon(Feather.MORE_VERTICAL));
        menuButton.getStyleClass().addAll(Styles.BUTTON_ICON, Tweaks.NO_ARROW);
        menuButton.getItems().addAll(
                openChatMenuItem,
                new SeparatorMenuItem(),
                removeMenuItem,
                new SeparatorMenuItem(),
                openPageMenuItem
        );
        tile.setAction(menuButton);

        var imageView = new LazyImageView();
        imageView.lazyImageProperty().bind(channel.profileImageProperty());
        imageView.setFitWidth(PROFILE_IMAGE_WIDTH);
        imageView.setFitHeight(PROFILE_IMAGE_HEIGHT);
        var clip = new Rectangle();
        clip.widthProperty().bind(imageView.fitWidthProperty());
        clip.heightProperty().bind(imageView.fitHeightProperty());
        clip.arcWidthProperty().bind(imageView.fitWidthProperty());
        clip.arcHeightProperty().bind(imageView.fitHeightProperty());
        imageView.setClip(clip);
        tile.setGraphic(imageView);
        installStreamInfoPopOver(channel, imageView);

        // ライブの状態に応じてエフェクトを切り替える
        updateLiveEffect(imageView, channel.isLive());
        channel.liveProperty().addListener((ob, o, n) -> updateLiveEffect(imageView, n));

        return tile;
    }

    private void updateLiveEffect(Node node, boolean live) {
        if (live) {
            node.setEffect(null);
        } else {
            node.setEffect(new SepiaTone());
        }
    }

    public static void installStreamInfoPopOver(TwitchChannelViewModel channel, Node node) {
        var gameNameLabel = new Label();
        gameNameLabel.setWrapText(true);

        var streamTitleLabel = new Label();
        streamTitleLabel.setWrapText(true);
        streamTitleLabel.getStyleClass().addAll(Styles.TEXT_SMALL);

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

        var popup = new LiveInfoPopup(channel);
        popup.setAutoHide(true);

        node.setOnMousePressed(e -> {
            if (channel.isLive() && !popup.isShowing()) {
                var bounds = JavaFXHelper.computeScreenBounds(node);
                var x = bounds.getMinX() - popup.getWidth() / 2 + bounds.getWidth() / 2;
                var y = bounds.getMaxY();
                popup.show(node, x, y);
                e.consume();
            }
        });
        node.setOnMouseEntered(e -> {
            if (channel.isLive()) {
                var bounds = JavaFXHelper.computeScreenBounds(node);
                var x = bounds.getMinX() - popup.getWidth() / 2 + bounds.getWidth() / 2;
                var y = bounds.getMaxY();
                popup.show(node, x, y);
            }
        });
        node.setOnMouseExited(e -> popup.hide());
    }

    @Override
    protected double computeMinWidth(double height) {
        return USE_COMPUTED_SIZE;
    }

    @Override
    protected double computePrefWidth(double height) {
        return USE_COMPUTED_SIZE;
    }

}
