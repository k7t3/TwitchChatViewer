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

import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class LoggerInitializer {

    private LoggerInitializer() {
    }

    public static LoggingSession initialize() {
        return initialize(OS.current().getApplicationDirectory(), Logger.getLogger(""),
                LoggerInitializer.class.getResourceAsStream("/logging.properties"));
    }

    /** 設定ストリームを消費・解放し、指定した logger のハンドラーだけを管理する。 */
    static LoggingSession initialize(Path directory, Logger target, InputStream source) {
        var properties = new Properties();
        try (var input = Objects.requireNonNull(source, "logging configuration is missing")) {
            properties.load(input);
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        var level = Level.parse(properties.getProperty(".level", "INFO"));
        var consoleLevel = Level.parse(properties.getProperty("java.util.logging.ConsoleHandler.level", "INFO"));
        var fileLevel = Level.parse(properties.getProperty("java.util.logging.FileHandler.level", "ALL"));
        var limit = Long.parseLong(properties.getProperty("java.util.logging.FileHandler.limit", "1073741824"));
        var count = Integer.parseInt(properties.getProperty("java.util.logging.FileHandler.count", "10"));
        if (limit < 0 || count < 1) throw new IllegalArgumentException("invalid log rotation settings");
        var formatter = new TextLogFormatter(properties.getProperty("java.util.logging.SimpleFormatter.format",
                "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$s [%2$s] %5$s %6$s%n"));
        var handlers = new ArrayList<Handler>();
        LoggingSession session = null;
        try {
            Files.createDirectories(directory);
            var console = new ConsoleHandler();
            handlers.add(console);
            console.setEncoding("UTF-8");
            console.setFormatter(formatter);
            console.setLevel(consoleLevel);
            // 明示的に生成し、FileHandler の開始エラーを起動失敗として呼出元へ返す。
            var file = new FileHandler(directory.resolve("log%g.log").toString(), limit, count, false);
            handlers.add(file);
            file.setEncoding("UTF-8");
            file.setFormatter(formatter);
            file.setLevel(fileLevel);
            synchronized (target) {
                session = new LoggingSession(target, handlers, target.getHandlers(), target.getLevel());
                for (var previous : session.borrowed) target.removeHandler(previous);
                for (var handler : handlers) target.addHandler(handler);
                target.setLevel(level);
            }
            return session;
        } catch (IOException | RuntimeException | Error failure) {
            var primary = failure instanceof IOException io ? new UncheckedIOException(io) : failure;
            var installed = session;
            CloseActions.runAll(() -> rethrow(primary), () -> {
                if (installed != null) installed.close();
                else CloseActions.runAll(handlers.stream().map(handler -> (Runnable) handler::close).toArray(Runnable[]::new));
            });
            throw new AssertionError("unreachable");
        }
    }

    /** アプリが導入したハンドラーだけを閉じ、借用した設定を戻す。 */
    public static final class LoggingSession implements AutoCloseable {
        private final Logger target;
        private final List<Handler> owned;
        private final Handler[] borrowed;
        private final Level previousLevel;
        private boolean closed;

        private LoggingSession(Logger target, List<Handler> owned, Handler[] borrowed, Level previousLevel) {
            this.target = target;
            this.owned = List.copyOf(owned);
            this.borrowed = borrowed;
            this.previousLevel = previousLevel;
        }

        @Override public void close() {
            synchronized (target) {
                if (closed) return;
                closed = true;
                var actions = new ArrayList<Runnable>();
                for (var handler : owned) {
                    actions.add(() -> target.removeHandler(handler));
                    actions.add(handler::close);
                }
                for (var handler : borrowed) actions.add(() -> {
                    if (Arrays.stream(target.getHandlers()).noneMatch(current -> current == handler)) target.addHandler(handler);
                });
                actions.add(() -> target.setLevel(previousLevel));
                CloseActions.runAll(actions.toArray(Runnable[]::new));
            }
        }
    }

    private static final class TextLogFormatter extends Formatter {
        private final String pattern;

        private TextLogFormatter(String pattern) {
            this.pattern = pattern;
            // リソースを作成する前に、6引数の書式が有効か確認する。
            render(ZonedDateTime.now(), "source", "logger", "INFO", "message", "");
        }

        @Override public String format(LogRecord record) {
            var source = record.getSourceClassName();
            if (source == null) source = record.getLoggerName();
            else if (record.getSourceMethodName() != null) source += " " + record.getSourceMethodName();
            var trace = new StringWriter();
            if (record.getThrown() != null) {
                var writer = new PrintWriter(trace);
                writer.println();
                record.getThrown().printStackTrace(writer);
                writer.flush();
            }
            return render(record.getInstant().atZone(ZoneId.systemDefault()), source, record.getLoggerName(),
                    record.getLevel().getLocalizedName(), formatMessage(record), trace.toString());
        }

        private String render(ZonedDateTime time, String source, String logger, String level, String message, String trace) {
            return String.format(pattern, time, source, logger, level, message, trace);
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        throw new RuntimeException(failure);
    }
}
