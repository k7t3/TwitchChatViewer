package com.github.k7t3.tcv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.logging.LogManager;

public class LoggerProvider {

    static {
        initialize(() -> LoggerProvider.class.getResourceAsStream("/logging_test.properties"));
    }

    public static Logger getLogger(Class<?> cls) {
        return LoggerFactory.getLogger(cls);
    }

    private static void initialize(Callable<InputStream> callable) {
        try (var is = callable.call()) {
            LogManager.getLogManager().readConfiguration(is);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private LoggerProvider() {
    }

}
