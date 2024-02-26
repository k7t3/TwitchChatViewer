package com.github.k7t3.tcv.view.clip;

import com.github.k7t3.tcv.app.clip.ClipPlayerViewModel;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.view.core.StageBoundsListener;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

class ClipPlayerStage extends Stage {

    private static final String WINDOW_NAME = "clip-player";

    private final ClipPlayerViewModel player;

    public ClipPlayerStage(Window owner, ClipPlayerViewModel player) {
        initOwner(owner);
        this.player = player;

        initView();
        initBounds();
    }

    private void initView() {
        var tuple = FluentViewLoader.fxmlView(ClipPlayerView.class)
                .viewModel(player)
                .resourceBundle(Resources.getResourceBundle())
                .load();

        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        codeBehind.getMediaView().fitWidthProperty().bind(widthProperty());
        codeBehind.getMediaView().fitHeightProperty().bind(heightProperty());

        setScene(new Scene(view));
    }

    private void initBounds() {
        var prefs = AppPreferences.getInstance().getWindowPreferences(WINDOW_NAME);
        var bounds = prefs.getStageBounds();
        bounds.apply(this);

        var listener = new StageBoundsListener();
        listener.install(this);

        setOnCloseRequest((WindowEvent e) -> {
            // ウインドウをの矩形を記録
            prefs.setStageBounds(listener.getCurrent());
            // プレイヤーを破棄
            player.dispose();
        });
    }

}
