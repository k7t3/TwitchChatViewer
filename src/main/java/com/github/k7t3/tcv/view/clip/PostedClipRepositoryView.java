package com.github.k7t3.tcv.view.clip;

import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.clip.PostedClipViewModel;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.view.web.BrowserController;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;

import java.net.URL;
import java.util.Comparator;
import java.util.ResourceBundle;

public class PostedClipRepositoryView implements FxmlView<PostedClipRepository>, Initializable {

    @FXML
    private Pane root;

    @FXML
    private Label titleLabel;

    @FXML
    private HBox channelOwnersContainer;

    @FXML
    private ListView<PostedClipViewModel> videoClips;

    @InjectViewModel
    private PostedClipRepository repository;

    private BrowserController browserController;

    private ObservableList<PostedClipViewModel> postedClips;
    private FilteredList<PostedClipViewModel> filteredClips;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        titleLabel.getStyleClass().add(Styles.TITLE_3);

        postedClips = FXCollections.observableArrayList();
        postedClips.addAll(repository.getPostedClips().values());
        repository.getPostedClips().addListener(this::onPostedClipChanged);

        initializeButtons();

        filteredClips = new FilteredList<>(postedClips);
        var sortedClips = new SortedList<>(filteredClips);
        sortedClips.setComparator(Comparator.comparing(PostedClipViewModel::getLastPostedAt));

        videoClips.setItems(sortedClips);
        videoClips.setCellFactory(param -> new PostedClipViewCell());

        videoClips.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) {
                openBrowser();
            }
        });
        videoClips.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                openBrowser();
            }
        });

        root.parentProperty().addListener((ob, o, n) -> {
            if (n == null) return;
            root.prefWidthProperty().bind(n.layoutBoundsProperty().map(b -> b.getWidth() * 0.4));
            root.prefHeightProperty().bind(n.layoutBoundsProperty().map(b -> b.getHeight() * 0.7));
        });
    }

    private void onPostedClipChanged(MapChangeListener.Change<? extends String, ? extends PostedClipViewModel> change) {
        if (change.wasAdded()) {
            postedClips.add(change.getValueAdded());
        }
        if (change.wasRemoved()) {
            postedClips.remove(change.getValueRemoved());
        }
    }

    public void setBrowserController(BrowserController browserController) {
        this.browserController = browserController;
    }

    private void openBrowser() {
        var selected = videoClips.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        var url = selected.getClip().url();
        browserController.load(url);
        browserController.show();
    }

    private void initializeButtons() {
        var group = new ToggleGroup();

        var buttons = repository.getPostedBroadcasters()
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
                filteredClips.setPredicate(posted -> posted.isPosted(channelOwner));
            } else {
                filteredClips.setPredicate(null);
            }
        });
        return button;
    }

}
