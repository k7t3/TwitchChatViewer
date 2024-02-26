package com.github.k7t3.tcv.view.clip;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.clip.ClipPlayerViewModel;
import com.github.k7t3.tcv.app.clip.VideoClipListViewModel;
import com.github.k7t3.tcv.app.clip.VideoClipViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.prefs.AppPreferences;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ResourceBundle;

public class VideoClipListView implements FxmlView<VideoClipListViewModel>, Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(VideoClipListViewModel.class);

    @FXML
    private Pane root;

    @FXML
    private Label titleLabel;

    @FXML
    private HBox channelOwnersContainer;

    @FXML
    private ListView<VideoClipViewModel> videoClips;

    @InjectViewModel
    private VideoClipListViewModel viewModel;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        var helper = AppHelper.getInstance();
        var twitch = helper.getTwitch();

        titleLabel.getStyleClass().add(Styles.TITLE_3);

        viewModel.installRepository(twitch.getClipRepository());

        initializeButtons();

        videoClips.setItems(viewModel.getClips());
        videoClips.setCellFactory(param -> new VideoClipViewCell());

        videoClips.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER)
                openPlayerIfEnabled();
        });
        videoClips.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2)
                openPlayerIfEnabled();
        });

        root.parentProperty().addListener((ob, o, n) -> {
            if (n == null) return;
            root.prefWidthProperty().bind(n.layoutBoundsProperty().map(b -> b.getWidth() * 0.4));
            root.prefHeightProperty().bind(n.layoutBoundsProperty().map(b -> b.getHeight() * 0.7));
        });
    }

    private void openPlayerIfEnabled() {
        // プレイヤーは非公式の機能を使用しているためExperimentalとする
        if (!AppPreferences.getInstance().isExperimental()) {
            LOGGER.warn("clip player is disabled!");
            return;
        }

        var selected = videoClips.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        var player = new ClipPlayerStage(root.getScene().getWindow(), new ClipPlayerViewModel(selected));
        player.setTitle(selected.getPosted().getClip().broadcasterName());
        player.show();
    }

    private void initializeButtons() {
        var group = new ToggleGroup();

        var buttons = viewModel.getChannelOwners()
                .stream()
                .map(channelOwner -> createButton(channelOwner, group))
                .toList();
        channelOwnersContainer.getChildren().addAll(buttons);
    }

    private ToggleButton createButton(Broadcaster channelOwner, ToggleGroup group) {
        var image = new Image(channelOwner.getProfileImageUrl(), 30, 30, true, true, true);
        var button = new ToggleButton("", new ImageView(image));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setToggleGroup(group);
        button.setOnAction(e -> {
            if (button.isSelected()) {
                viewModel.filter(channelOwner);
            } else {
                viewModel.filter(null);
            }
        });
        return button;
    }

}
