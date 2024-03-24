package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.prefs.AppPreferences;
import javafx.application.Platform;
import org.slf4j.LoggerFactory;

public class Shutdown {

    private Shutdown() {
    }

    public static void exit() {
        var log = LoggerFactory.getLogger(Shutdown.class);
        log.info("application closing ...");

        // 設定のSave
        var prefs = AppPreferences.getInstance();
        prefs.save();

        // アプリケーションの終了
        var helper = AppHelper.getInstance();
        helper.close();

        // JavaFX Application Threadの停止
        Platform.exit();
    }

}
