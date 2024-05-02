package com.github.k7t3.tcv.view.clip;

import atlantafx.base.controls.Popover;
import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.clip.EstimatedClipViewModel;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.clip.PostedClipViewModel;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.app.core.Resources;
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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.net.URL;
import java.util.ResourceBundle;

public class PostedClipRepositoryView implements FxmlView<PostedClipRepository>, Initializable {

    @FXML
    private SplitPane root;

    @FXML
    private HBox channelOwnersContainer;

    @FXML
    private ListView<PostedClipViewModel> videoClips;

    @FXML
    private Node helpIcon;

    @FXML
    private ListView<EstimatedClipViewModel> estimatedClipURLs;

    @InjectViewModel
    private PostedClipRepository repository;

    private BrowserController browserController;

    private ObservableList<PostedClipViewModel> postedClips;
    private FilteredList<PostedClipViewModel> filteredClips;

    private ObservableList<EstimatedClipViewModel> estimatedClips;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        postedClips = FXCollections.observableArrayList();
        postedClips.addAll(repository.getPostedClips().values());
        repository.getPostedClips().addListener(this::onPostedClipChanged);

        estimatedClips = FXCollections.observableArrayList();
        estimatedClips.addAll(repository.getEstimatedClipURLs().values());
        repository.getEstimatedClipURLs().addListener(this::onPostedEstimatedClipChanged);

        initializeButtons();
        installHelpMessage();

        filteredClips = new FilteredList<>(postedClips);
        var sortedClips = new SortedList<>(filteredClips);
        sortedClips.setComparator((o1, o2) -> o2.getLastPostedAt().compareTo(o1.getLastPostedAt()));

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

        estimatedClipURLs.setItems(estimatedClips);
        estimatedClipURLs.setCellFactory(p -> new EstimatedClipURLCell(browserController));

        root.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        root.parentProperty().addListener((ob, o, n) -> {
            if (n == null) return;
            root.prefWidthProperty().bind(n.layoutBoundsProperty().map(b -> b.getWidth() * 0.6));
            root.prefHeightProperty().bind(n.layoutBoundsProperty().map(b -> b.getHeight() * 0.8));
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

    private void onPostedEstimatedClipChanged(MapChangeListener.Change<? extends String, ? extends EstimatedClipViewModel> change) {
        if (change.wasAdded()) {
            estimatedClips.add(change.getValueAdded());
        }
        if (change.wasRemoved()) {
            estimatedClips.remove(change.getValueRemoved());
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

    private void installHelpMessage() {
        var label = new Label(Resources.getString("clip.help"));
        label.getStyleClass().addAll(Styles.TEXT_SMALL);
        label.setWrapText(true);

        var popOver = new Popover();
        popOver.setDetachable(false);
        popOver.setContentNode(label);
        popOver.setArrowLocation(Popover.ArrowLocation.TOP_LEFT);
        popOver.setPrefWidth(200);
        popOver.setAutoHide(true);
        //popOver.setCloseButtonEnabled(true);

        // TitledPaneのスタイルに影響されてタイトルが巨大になってしまうため非表示
        popOver.setHeaderAlwaysVisible(false);
        popOver.setTitle("What is this?");

        helpIcon.setOnMouseClicked(e -> {
            e.consume();
            popOver.show(helpIcon);
        });
    }

}
