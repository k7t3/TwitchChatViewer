package com.github.k7t3.tcv.view.clip;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.clip.VideoClipListViewModel;
import com.github.k7t3.tcv.app.clip.VideoClipViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

import java.net.URL;
import java.util.ResourceBundle;

public class VideoClipListView implements FxmlView<VideoClipListViewModel>, Initializable {

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

        root.parentProperty().addListener((ob, o, n) -> {
            if (n == null) return;
            root.prefWidthProperty().bind(n.layoutBoundsProperty().map(b -> b.getWidth() * 0.5));
            root.prefHeightProperty().bind(n.layoutBoundsProperty().map(b -> b.getHeight() * 0.7));
        });
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
        var image = channelOwner.getProfileImageUrl()
                .map(url -> new Image(url, 30, 30, true, true, true))
                .orElse(null);
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
