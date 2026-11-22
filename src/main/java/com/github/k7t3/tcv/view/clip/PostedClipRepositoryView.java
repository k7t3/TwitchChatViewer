/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.view.clip;

import com.github.k7t3.tcv.app.clip.PostedClipItem;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.view.image.LazyImageView;
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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javafx.beans.value.ChangeListener;
import javafx.scene.Parent;
import javafx.collections.ObservableMap;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.util.ResourceBundle;

public class PostedClipRepositoryView implements FxmlView<PostedClipRepository>, Initializable, AutoCloseable {
    private boolean closed;
    private ObservableMap<String, PostedClipItem> source;
    private final MapChangeListener<String, PostedClipItem> itemsChanged = this::onPostedClipChanged;
    private final ChangeListener<Parent> parentChanged = (observable, old, value) -> bindSize(value);
    private final List<PostedClipViewCell> cells = new ArrayList<>();


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
        if (closed) throw new IllegalStateException("clip view is closed");
        postedClips = FXCollections.observableArrayList();
        source = repository.getItems();
        postedClips.addAll(source.values());
        source.addListener(itemsChanged);

        initializeButtons();

        filteredClips = new FilteredList<>(postedClips);
        var sortedClips = new SortedList<>(filteredClips);
        sortedClips.setComparator((o1, o2) -> o2.getLastPostedAt().compareTo(o1.getLastPostedAt()));

        videoClips.setItems(sortedClips);
        videoClips.setCellFactory(param -> { var cell = new PostedClipViewCell(); cells.add(cell); return cell; });

        root.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        root.parentProperty().addListener(parentChanged);
        bindSize(root.getParent());
    }

    private void bindSize(Parent parent) {
        root.prefWidthProperty().unbind(); root.prefHeightProperty().unbind();
        if (!closed && parent != null) {
            root.prefWidthProperty().bind(parent.layoutBoundsProperty().map(bounds -> bounds.getWidth() * 0.6));
            root.prefHeightProperty().bind(parent.layoutBoundsProperty().map(bounds -> bounds.getHeight() * 0.8));
        }
    }

    private void onPostedClipChanged(MapChangeListener.Change<? extends String, ? extends PostedClipItem> change) {
        if (closed) return;
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
        var button = new ToggleButton("", new LazyImageView(repository.getBroadcasterImage(channelOwner)));
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setAccessibleText(channelOwner.getUserName());
        button.setTooltip(new javafx.scene.control.Tooltip(channelOwner.getUserName()));
        button.setToggleGroup(group);
        button.setOnAction(e -> {
            if (closed) return;
            if (button.isSelected()) {
                filteredClips.setPredicate(posted -> posted.isPosted(channelOwner));
            } else {
                filteredClips.setPredicate(null);
            }
        });
        return button;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CloseActions.runAll(() -> { if (source != null) source.removeListener(itemsChanged); }, () -> {
            if (root != null) { root.parentProperty().removeListener(parentChanged); bindSize(null); }
            if (videoClips != null) { videoClips.setItems(null); videoClips.setCellFactory(null); }
        }, () -> {
            var owned = List.copyOf(cells); cells.clear();
            CloseActions.runAll(owned.stream().map(cell -> (Runnable) cell::close).toArray(Runnable[]::new));
        }, () -> {
            if (channelOwnersContainer != null) {
                for (var node : channelOwnersContainer.getChildren()) {
                    if (node instanceof ToggleButton button) {
                        button.setOnAction(null); button.setToggleGroup(null);
                        if (button.getGraphic() instanceof LazyImageView image) { image.lazyImageProperty().unbind(); image.setLazyImage(null); }
                        if (button.getTooltip() != null) button.getTooltip().hide();
                        button.setGraphic(null); button.setTooltip(null);
                    }
                }
                channelOwnersContainer.getChildren().clear();
            }
            if (postedClips != null) postedClips.clear();
        });
    }

}
