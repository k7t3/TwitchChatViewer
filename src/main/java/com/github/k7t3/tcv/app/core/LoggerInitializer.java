package com.github.k7t3.tcv.app.core;

import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.logging.LogManager;

public class LoggerInitializer {

    private LoggerInitializer() {
    }

    public static void initialize() {
        initialize(() -> LoggerInitializer.class.getResourceAsStream("/logging.properties"));
    }

    private static void initialize(Callable<InputStream> callable) {
        try (var is = callable.call()) {
            LogManager.getLogManager().updateConfiguration(is, k -> ((o, n) -> {
                if (k.equals("java.util.logging.FileHandler.pattern")) {
                    var appDir = OS.current().getApplicationDirectory();
                    var logFile = appDir.resolve("log%g.log");
                    return logFile.toString();
                } else {
                    return n;
                }
            }));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
