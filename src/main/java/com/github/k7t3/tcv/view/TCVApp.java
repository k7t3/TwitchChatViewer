package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.LoggerInitializer;
import com.github.k7t3.tcv.app.core.Shutdown;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.view.core.StageBoundsListener;
import com.github.k7t3.tcv.view.core.ThemeManager;
import com.github.k7t3.tcv.view.core.WindowEventHelper;
import com.github.k7t3.tcv.view.main.MainView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TCVApp extends Application {

    private Logger log;

    private AppPreferences preferences;

    @Override
    public void init() throws Exception {
        super.init();

        //
        // 暗黙的な終了を無効化
        // アプリケーションの終了はShutdownクラスを使用する
        //
        Platform.setImplicitExit(false);

        // ロガーの初期化
        LoggerInitializer.initialize();

        log = LoggerFactory.getLogger(TCVApp.class);
        log.info("logger initialized");

        preferences = AppPreferences.getInstance();
    }

    @Override
    public void start(Stage primaryStage) {

        log.info("start application");

        var helper = AppHelper.getInstance();
        helper.setPrimaryStage(primaryStage);

        var tuple = FluentViewLoader.fxmlView(MainView.class)
                .resourceBundle(Resources.getResourceBundle()).load();
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
        WindowEventHelper.closed(primaryStage, Shutdown::exit);

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
