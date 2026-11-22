package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.domain.auth.CredentialStore;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.app.theme.ThemeManager;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.WindowBounds;
import com.github.k7t3.tcv.view.core.WindowBoundsListener;
import com.github.k7t3.tcv.view.main.MainView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.util.function.Consumer;

/** FX 側の画面、テーマ、ウインドウ購読を、ロードの開始前から所有する。 */
final class MainWindow implements ApplicationWindow {
    private final Stage stage;
    private final MainViewModel model;
    private final AppHelper helper;
    private final AppPreferences preferences;
    private final WindowBounds savedBounds;
    private final MainView controller;
    private final ThemeManager theme;
    private final Runnable hidden;
    private final Consumer<Throwable> failed;
    private final WindowBoundsListener bounds = new WindowBoundsListener();
    private WindowBoundsService boundsService;
    private EventHandler<WindowEvent> onShown;
    private EventHandler<WindowEvent> onHiding;
    private EventHandler<WindowEvent> onHidden;
    private boolean shown;
    private boolean closed;

    MainWindow(Stage stage, MainViewModel model, AppHelper helper, AppPreferences preferences, WindowBounds savedBounds,
               Runnable hidden, Consumer<Throwable> failed, CredentialStore credentials) {
        this(stage, model, helper, preferences, savedBounds, hidden, failed, credentials, new ThemeManager());
    }

    private MainWindow(Stage stage, MainViewModel model, AppHelper helper, AppPreferences preferences, WindowBounds savedBounds,
                       Runnable hidden, Consumer<Throwable> failed, CredentialStore credentials, ThemeManager theme) {
        this(stage, model, helper, preferences, savedBounds, hidden, failed,
                MainViewFactory.create(stage, model, helper, preferences, theme, credentials), theme);
    }

    MainWindow(Stage stage, MainViewModel model, AppHelper helper, AppPreferences preferences, WindowBounds savedBounds, Runnable hidden,
               Consumer<Throwable> failed, MainView controller, ThemeManager theme) {
        this.stage = stage;
        this.model = model;
        this.helper = helper;
        this.preferences = preferences;
        this.savedBounds = savedBounds;
        this.hidden = hidden;
        this.failed = failed;
        this.controller = controller;
        this.theme = theme;
    }

    @Override public void show() {
        if (closed || shown) throw new IllegalStateException("window is already opened or closed");
        var tuple = FluentViewLoader.fxmlView(MainView.class).codeBehind(controller).viewModel(model)
                .resourceBundle(Resources.getResourceBundle()).load();
        if (closed) return;
        var scene = new Scene(tuple.getView());
        theme.setScene(scene);
        theme.setTheme(preferences.getGeneralPreferences().getTheme());
        if (closed) return;
        theme.setThemeType(preferences.getGeneralPreferences().getThemeType());
        if (closed) return;
        stage.titleProperty().bind(model.titleProperty());
        if (closed) return;
        stage.getIcons().setAll(Resources.getIcons());
        if (closed) return;
        bounds.install(stage);
        var service = helper.getWindowBoundsService();
        boundsService = service;
        savedBounds.apply(stage, () -> !closed);
        if (closed) return;
        onShown = event -> {
            stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, onShown);
            if (closed) return;
            try { controller.startMainView(); }
            catch (RuntimeException | Error failure) { failed.accept(failure); }
        };
        onHiding = event -> { if (shown && !closed) service.saveBoundsAsync("main", bounds.getCurrent()); };
        onHidden = event -> hidden.run();
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, onShown);
        stage.addEventHandler(WindowEvent.WINDOW_HIDING, onHiding);
        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, onHidden);
        stage.setScene(scene);
        if (closed) return;
        stage.show();
        if (!closed) shown = true;
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        CloseActions.runAll(() -> {
            // onHidden より先に終了要求が来た場合も、最後の境界を排出対象へ入れる。
            if (shown && stage.isShowing()) boundsService.saveBoundsAsync("main", bounds.getCurrent());
        }, () -> {
            if (onShown != null) stage.removeEventHandler(WindowEvent.WINDOW_SHOWN, onShown);
            if (onHiding != null) stage.removeEventHandler(WindowEvent.WINDOW_HIDING, onHiding);
            if (onHidden != null) stage.removeEventHandler(WindowEvent.WINDOW_HIDDEN, onHidden);
        }, bounds::uninstall, controller::close, theme::close, () -> {
            stage.titleProperty().unbind();
            stage.hide();
            stage.setScene(null);
            if (helper.getPrimaryStage() == stage) helper.setPrimaryStage(null);
        });
    }
}
