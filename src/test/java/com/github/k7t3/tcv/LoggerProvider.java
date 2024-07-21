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

    public static void init() {
        // dummy
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
