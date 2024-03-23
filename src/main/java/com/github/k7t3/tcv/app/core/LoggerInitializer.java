package com.github.k7t3.tcv.app.core;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.logging.LogManager;

public class LoggerInitializer {

    private LoggerInitializer() {
    }

    public static void initialize() {
        var file = Path.of("logging.properties");
        if (Files.exists(file)) {
            initialize(() -> Files.newInputStream(file));
        } else {
            initialize(() -> LoggerInitializer.class.getResourceAsStream("/logging.properties"));
        }
    }

    private static void initialize(Callable<InputStream> callable) {
        try (var is = callable.call()) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
