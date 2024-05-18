package com.github.k7t3.tcv.view.clip;

import com.github.k7t3.tcv.app.clip.ClipPlayerViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.core.WindowBoundsListener;
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
        var service = AppHelper.getInstance().getWindowBoundsService();
        service.getBoundsAsync(WINDOW_NAME).onDone(bounds -> bounds.apply(this));

        var listener = new WindowBoundsListener();
        listener.install(this);

        setOnCloseRequest((WindowEvent e) -> {
            // ウインドウをの矩形を記録
            service.saveBoundsAsync(WINDOW_NAME, listener.getCurrent());
            // プレイヤーを破棄
            player.dispose();
        });
    }

}
