package com.github.k7t3.tcv.view.clip;

import com.github.k7t3.tcv.app.clip.ClipPlayerViewModel;
import com.github.k7t3.tcv.app.clip.VideoClipViewModel;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.view.core.StageBoundsListener;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.List;

class ClipPlayerStage extends Stage {

    private static final String WINDOW_NAME = "clip-player";

    private final TabPane tabPane;

    private final ObservableList<ClipPlayerViewModel> players;

    public ClipPlayerStage(Window owner, ObservableList<ClipPlayerViewModel> players) {
        initOwner(owner);
        this.players = players;

        tabPane = new TabPane();
        setScene(new Scene(tabPane));

        players.addListener(this::playerChanged);
        addPlayers(players);

        initBounds();
    }

    private void addPlayers(List<? extends ClipPlayerViewModel> players) {
        for (var playerViewModel : players) {

            var tuple = FluentViewLoader.fxmlView(ClipPlayerView.class)
                    .viewModel(playerViewModel)
                    .resourceBundle(Resources.getResourceBundle())
                    .load();

            var view = tuple.getView();
            var codeBehind = tuple.getCodeBehind();

            codeBehind.getMediaView().fitWidthProperty().bind(widthProperty());
            codeBehind.getMediaView().fitHeightProperty().bind(heightProperty());

            var tab = new Tab();
            tab.textProperty().bind(playerViewModel.clipProperty().map(VideoClipViewModel::getTitle));
            tab.setContent(view);
            tab.setOnCloseRequest(e -> playerViewModel.dispose());
            tab.setOnClosed(e -> {
                if (tabPane.getTabs().isEmpty())
                    close();
            });

            tabPane.getTabs().add(tab);
        }
    }

    private void playerChanged(ListChangeListener.Change<? extends ClipPlayerViewModel> c) {
        while (c.next()) {
            if (c.wasAdded()) {
                addPlayers(c.getAddedSubList());
            }
        }
    }

    private void initBounds() {
        var prefs = AppPreferences.getInstance().getWindowPreferences(WINDOW_NAME);
        var bounds = prefs.getStageBounds();
        bounds.apply(this);

        var listener = new StageBoundsListener();
        listener.install(this);

        setOnCloseRequest((WindowEvent e) -> {
            prefs.setStageBounds(listener.getCurrent());

            for (var playerViewModel : players)
                playerViewModel.dispose();
        });
    }

}
