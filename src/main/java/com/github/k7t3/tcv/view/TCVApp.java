package com.github.k7t3.tcv.view;

import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.NordLight;
import atlantafx.base.theme.PrimerLight;
import com.github.k7t3.tcv.view.main.MainView;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.vm.core.AppHelper;
import com.github.k7t3.tcv.vm.service.WindowEventHelper;
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
    public void start(Stage primaryStage) throws Exception {
        //Application.setUserAgentStylesheet(new CupertinoDark().getUserAgentStylesheet());
        Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
        //Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        var loader = FluentViewLoader.fxmlView(MainView.class);
        loader.resourceBundle(Resources.getResourceBundle());
        var tuple = loader.load();
        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        var scene = new Scene(view);

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

//        AppHelper.getInstance().setPrimaryStage(primaryStage);
//
//        // ルート
//        var root = new RootPane();
//        // 通知用ノードの設定
//        MessageNotificator.setRoot(root);
//
//        var loader = FluentViewLoader.fxmlView(FollowChannelsView.class);
//        loader.resourceBundle(Resources.getResourceBundle());
//        var tuple = loader.load();
//        var view = tuple.getView();
//        var viewModel = tuple.getViewModel();
//        root.getContentContainer().getChildren().add(view);
//
//        Runnable callback = () -> {
//            try {
//                authDialog = null;
//                var twitch = AppHelper.getInstance();
//                viewModel.setChannelRepository(new ChannelRepository(twitch.getTwitch()));
//                viewModel.loadAsync();
//            } catch (Exception e) {
//                ExceptionHandler.handle(primaryStage, e);
//            }
//        };
//
//        // 認証
//        WindowEventHelper.shownOnce(primaryStage, e -> {
//            authDialog = new AuthenticatorDialog(root.getModalPane(), callback);
//            authDialog.load();
//        });
//
//        // クローズ
//        WindowEventHelper.closed(primaryStage, () -> {
//            if (authDialog != null) {
//                authDialog.cleanup();
//            }
//            var app = AppHelper.getInstance();
//            app.close();
//        });
//
//        primaryStage.setScene(new Scene(root, 1024, 768));
//        primaryStage.show();
    }

}
