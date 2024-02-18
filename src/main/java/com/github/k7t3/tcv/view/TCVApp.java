package com.github.k7t3.tcv.view;

import atlantafx.base.theme.*;
import com.github.k7t3.tcv.view.core.ThemeManager;
import com.github.k7t3.tcv.view.main.MainView;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.view.core.WindowEventHelper;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.LogManager;

public class TCVApp extends Application {

    @Override
    public void init() throws Exception {
        super.init();
        initializeLogger();
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

        var tm = ThemeManager.getInstance();
        tm.setScene(scene);
        tm.setTheme(new NordLight());

        // 画面が表示されたら認証画面を表示する(おそらくModalPaneの仕様的に
        // シーングラフが表示されてからじゃないと動作しないため)
        WindowEventHelper.shownOnce(primaryStage, e ->
                codeBehind.loadAuthorizationView());

        // ウインドウを閉じたときの処理
        WindowEventHelper.closed(primaryStage, () -> {
            var helper = AppHelper.getInstance();
            helper.close();
        });

        primaryStage.setScene(scene);
        primaryStage.show();
    }

}
