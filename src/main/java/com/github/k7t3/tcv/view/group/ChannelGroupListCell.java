package com.github.k7t3.tcv.view.group;

import atlantafx.base.controls.Card;
import atlantafx.base.controls.Spacer;
import atlantafx.base.controls.Tile;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.group.ChannelGroup;
import com.github.k7t3.tcv.app.group.ChannelGroupListViewModel;
import com.github.k7t3.tcv.view.control.EditableLabel;
import com.github.k7t3.tcv.view.group.menu.OpenBrowserMenuItem;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.MenuButton;
import javafx.scene.effect.SepiaTone;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.TilePane;
import javafx.scene.shape.Rectangle;
import org.fxmisc.flowless.Cell;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public class ChannelGroupListCell extends Card implements Cell<ChannelGroup, Region> {

    private static final String STYLE_CLASS = "channel-group-cell";
    private static final String TILE_STYLE_CLASS = "channel-group-tile";

    private static final double PROFILE_IMAGE_WIDTH = 48;
    private static final double PROFILE_IMAGE_HEIGHT = 48;

    private final EditableLabel header = new EditableLabel();

    private final EditableLabel subHeader = new EditableLabel();

    private final TilePane tilePane = new TilePane();

    private final ChannelGroup group;

    private final ChannelGroupListViewModel viewModel;

    public ChannelGroupListCell(ChannelGroup group, ChannelGroupListViewModel viewModel) {
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

        var deleteButton = new Button(Resources.getString("group.button.delete"), new FontIcon(Feather.TRASH));
        deleteButton.getStyleClass().addAll(Styles.DANGER);

        var footer = new HBox(deleteButton);
        footer.setPadding(new Insets(4));
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setSpacing(4);
        setFooter(footer);

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
                .map(this::createChannelNode)
                .forEach(tilePane.getChildren()::add);
    }

    private Node createChannelNode(TwitchChannelViewModel channel) {
        var tile = new Tile();
        tile.getStyleClass().add(TILE_STYLE_CLASS);
        tile.setPrefWidth(USE_COMPUTED_SIZE);
        tile.titleProperty().bind(channel.liveProperty().map(live -> live ? "LIVE" : null));
        tile.descriptionProperty().bind(channel.observableUserName());

        var menuButton = new MenuButton(null, new FontIcon(Feather.MORE_VERTICAL));
        menuButton.getStyleClass().addAll(Styles.BUTTON_ICON, Tweaks.NO_ARROW);
        initChannelMenu(menuButton, channel);
        tile.setAction(menuButton);

        var imageView = new ImageView();
        imageView.imageProperty().bind(channel.profileImageProperty());
        imageView.setFitWidth(PROFILE_IMAGE_WIDTH);
        imageView.setFitHeight(PROFILE_IMAGE_HEIGHT);
        var clip = new Rectangle();
        clip.widthProperty().bind(imageView.fitWidthProperty());
        clip.heightProperty().bind(imageView.fitHeightProperty());
        clip.arcWidthProperty().bind(imageView.fitWidthProperty());
        clip.arcHeightProperty().bind(imageView.fitHeightProperty());
        imageView.setClip(clip);
        tile.setGraphic(imageView);

        // ライブの状態に応じてエフェクトを切り替える
        updateLiveEffect(imageView, channel.isLive());
        channel.liveProperty().addListener((ob, o, n) -> updateLiveEffect(imageView, n));

        return tile;
    }

    private void initChannelMenu(MenuButton menu, TwitchChannelViewModel channel) {
        // チャンネルページをブラウザで開く
        var openPageMenuItem = new OpenBrowserMenuItem(channel);

        menu.getItems().addAll(openPageMenuItem);
    }

    private void updateLiveEffect(Node node, boolean live) {
        if (live) {
            node.setEffect(null);
        } else {
            node.setEffect(new SepiaTone());
        }
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
