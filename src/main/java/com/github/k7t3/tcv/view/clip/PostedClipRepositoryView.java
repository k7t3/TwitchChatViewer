package com.github.k7t3.tcv.view.clip;

import com.github.k7t3.tcv.app.clip.PostedClipItem;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.collections.FXCollections;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.net.URL;
import java.util.ResourceBundle;

public class PostedClipRepositoryView implements FxmlView<PostedClipRepository>, Initializable {

    @FXML
    private BorderPane root;

    @FXML
    private HBox channelOwnersContainer;

    @FXML
    private ListView<PostedClipItem> videoClips;

    @InjectViewModel
    private PostedClipRepository repository;

    private ObservableList<PostedClipItem> postedClips;
    private FilteredList<PostedClipItem> filteredClips;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        postedClips = FXCollections.observableArrayList();
        postedClips.addAll(repository.getItems().values());
        repository.getItems().addListener(this::onPostedClipChanged);

        initializeButtons();

        filteredClips = new FilteredList<>(postedClips);
        var sortedClips = new SortedList<>(filteredClips);
        sortedClips.setComparator((o1, o2) -> o2.getLastPostedAt().compareTo(o1.getLastPostedAt()));

        videoClips.setItems(sortedClips);
        videoClips.setCellFactory(param -> new PostedClipViewCell());

        root.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        root.parentProperty().addListener((ob, o, n) -> {
            if (n == null) return;
            root.prefWidthProperty().bind(n.layoutBoundsProperty().map(b -> b.getWidth() * 0.6));
            root.prefHeightProperty().bind(n.layoutBoundsProperty().map(b -> b.getHeight() * 0.8));
        });
    }

    private void onPostedClipChanged(MapChangeListener.Change<? extends String, ? extends PostedClipItem> change) {
        if (change.wasAdded()) {
            postedClips.add(change.getValueAdded());
        }
        if (change.wasRemoved()) {
            postedClips.remove(change.getValueRemoved());
        }
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
