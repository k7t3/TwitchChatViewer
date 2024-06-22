package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.core.LoggerInitializer;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import com.github.k7t3.tcv.view.core.ThemeManager;
import com.github.k7t3.tcv.view.core.WindowBoundsListener;
import com.github.k7t3.tcv.view.main.MainView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class TCVApp extends Application {

    private Logger log;

    private AppPreferences preferences;

    private MainViewModel viewModel;

    @Override
    public void init() throws Exception {
        super.init();

        // 暗黙的な終了を無効化
        // アプリケーションは明示的に終了する。
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

        try {
            // ユーザーファイルを接続
            var generalPrefs = preferences.getGeneralPreferences();
            var userDataFile = new UserDataFile(generalPrefs.getUserDataFilePath());
            userDataFile.connectDatabase(generalPrefs.getDatabaseVersion());
            helper.setUserDataFile(userDataFile);
        } catch (IOException e) {
            ExceptionHandler.handle(e);
            Platform.exit();
        }

        // ViewModel
        viewModel = new MainViewModel(helper.getChannelGroupRepository());

        // チャンネルリストに関する設定のバインド
        var channelList = viewModel.getChannelListViewModel();
        channelList.bindGeneralPreferences(preferences.getGeneralPreferences());

        // チャットに関する設定のバインド
        var chatContainer = viewModel.getChatContainer();
        chatContainer.bindChatPreferences(preferences.getChatPreferences());
        chatContainer.bindChatMessageFilterPreferences(preferences.getMessageFilterPreferences());

        // Viewのロード
        var tuple = FluentViewLoader.fxmlView(MainView.class)
                .viewModel(viewModel)
                .resourceBundle(Resources.getResourceBundle())
                .load();
        var view = tuple.getView();
        var codeBehind = tuple.getCodeBehind();

        // メインシーンの作成
        var scene = new Scene(view);

        // テーマを適用
        var tm = ThemeManager.getInstance();
        tm.setScene(scene);
        tm.setTheme(preferences.getGeneralPreferences().getTheme());

        // 画面が表示されたら認証画面を表示する(おそらくModalPaneの仕様的に
        // シーングラフが表示されてからじゃないと動作しないため)
        JavaFXHelper.shownOnce(primaryStage, e -> codeBehind.startMainView());

        // ウインドウを閉じるときのイベント
        primaryStage.setOnHidden(this::onHidden);

        // ウインドウの情報
        primaryStage.titleProperty().bind(viewModel.titleProperty());
        primaryStage.getIcons().setAll(Resources.getIcons());

        // ウインドウの境界を追跡
        var boundsListener = new WindowBoundsListener();
        boundsListener.install(primaryStage);

        // ウインドウ境界を復元
        var windowBoundsService = helper.getWindowBoundsService();
        var windowBounds = windowBoundsService.getBounds("main");
        log.info("fetch window bounds {}", windowBounds);
        windowBounds.apply(primaryStage);

        // ウインドウを閉じるときに境界を記録
        primaryStage.addEventHandler(WindowEvent.WINDOW_HIDING, e -> {
            var t = windowBoundsService.saveBoundsAsync("main", boundsListener.getCurrent());
            t.waitForDone();
        });

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        log.info("application closing ...");

        // 設定のSave
        var prefs = AppPreferences.getInstance();
        prefs.save();

        // ViewModelのクローズ
        viewModel.close();

        // アプリケーションの終了
        var helper = AppHelper.getInstance();
        helper.close();
    }

    private void onHidden(WindowEvent e) {
        // JavaFX Applicationの終了
        Platform.exit();
    }

}
