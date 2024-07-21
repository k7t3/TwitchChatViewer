/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
