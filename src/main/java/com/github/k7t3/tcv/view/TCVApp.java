package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.view.core.StageBoundsListener;
import com.github.k7t3.tcv.view.core.ThemeManager;
import com.github.k7t3.tcv.view.core.WindowEventHelper;
import com.github.k7t3.tcv.view.main.MainView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.LogManager;

public class TCVApp extends Application {

    private AppPreferences preferences;

    @Override
    public void init() throws Exception {
        super.init();
        initializeLogger();
        preferences = AppPreferences.getInstance();
    }

    private void initializeLogger() {
        try (var is = getClass().getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void start(Stage primaryStage) {

        var loader = FluentViewLoader.fxmlView(MainView.class);
        loader.resourceBundle(Resources.getResourceBundle());

        var tuple = loader.load();
        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var scene = new Scene(view);

        // テーマ
        var tm = ThemeManager.getInstance();
        tm.setScene(scene);
        tm.setTheme(preferences.getTheme());

        primaryStage.setTitle("Twitch Chat Viewer");
        primaryStage.getIcons().setAll(Resources.getIcons());

        // 画面が表示されたら認証画面を表示する(おそらくModalPaneの仕様的に
        // シーングラフが表示されてからじゃないと動作しないため)
        WindowEventHelper.shownOnce(primaryStage, e ->
                codeBehind.startMainView());

        // ウインドウを閉じたときの処理
        WindowEventHelper.closed(primaryStage, () -> {
            var helper = AppHelper.getInstance();
            helper.close();
            preferences.save();
        });

        // ウインドウの境界を追跡
        var boundsListener = new StageBoundsListener();
        boundsListener.install(primaryStage);

        // ウインドウ境界を復元
        var windowPrefs = preferences.getWindowPreferences("main");
        var bounds = windowPrefs.getStageBounds();
        bounds.apply(primaryStage);

        // ウインドウを閉じるときに境界を記録
        primaryStage.setOnCloseRequest(e ->
                windowPrefs.setStageBounds(boundsListener.getCurrent()));

        primaryStage.setScene(scene);
        primaryStage.show();
    }

}
