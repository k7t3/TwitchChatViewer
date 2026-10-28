package com.github.k7t3.tcv.app.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoggerInitializerTest {
    @TempDir Path directory;

    @Test
    void firstLaunchCreatesTheDirectoryAndWritesUtf8BeforeReleasingTheLock() throws Exception {
        var target = isolatedLogger();
        var logs = directory.resolve("first/application");
        try (var session = LoggerInitializer.initialize(logs, target, configuration())) {
            target.log(Level.WARNING, "日本語のログ", new IllegalArgumentException("原因"));
            assertThat(logs.resolve("log0.log.lck")).exists();
            assertThat(Files.readString(logs.resolve("log0.log"), StandardCharsets.UTF_8))
                    .contains(Level.WARNING.getLocalizedName(), "日本語のログ", "IllegalArgumentException: 原因")
                    .matches("(?s)\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3} .*日本語のログ.*");
        }
        assertThat(logs.resolve("log0.log.lck")).doesNotExist();
        assertThat(target.getHandlers()).isEmpty();
    }

    @Test
    void closeRestoresBorrowedHandlersAndLevelWithoutClosingThem() {
        var target = isolatedLogger();
        var borrowed = mock(Handler.class);
        var addedLater = mock(Handler.class);
        target.addHandler(borrowed);
        target.setLevel(Level.WARNING);
        var session = LoggerInitializer.initialize(directory, target, configuration());
        assertThat(target.getHandlers()).doesNotContain(borrowed);
        assertThat(target.getLevel()).isEqualTo(Level.INFO);
        target.addHandler(addedLater);
        session.close();
        session.close();
        assertThat(target.getHandlers()).containsExactly(addedLater, borrowed);
        assertThat(target.getLevel()).isEqualTo(Level.WARNING);
        verify(borrowed, never()).close();
        verify(addedLater, never()).close();
    }

    @Test
    void invalidFormatCannotCreateAFileOrReplaceExistingLogging() {
        var target = isolatedLogger();
        var borrowed = mock(Handler.class);
        target.addHandler(borrowed);
        var invalid = new ByteArrayInputStream("java.util.logging.SimpleFormatter.format=%7$s".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> LoggerInitializer.initialize(directory, target, invalid)).isInstanceOf(IllegalArgumentException.class);
        assertThat(target.getHandlers()).containsExactly(borrowed);
        assertThat(directory.toFile().list()).isEmpty();
        verify(borrowed, never()).close();
    }

    @Test
    void anUnusableDirectoryFailsExplicitlyAndPreservesTheLogger() throws Exception {
        var target = isolatedLogger();
        var borrowed = mock(Handler.class);
        target.addHandler(borrowed);
        var file = Files.writeString(directory.resolve("file"), "keep");
        assertThatThrownBy(() -> LoggerInitializer.initialize(file, target, configuration())).isInstanceOf(UncheckedIOException.class);
        assertThat(Files.readString(file)).isEqualTo("keep");
        assertThat(target.getHandlers()).containsExactly(borrowed);
        verify(borrowed, never()).close();
    }

    @Test
    void partialInstallationRestoresHandlersAndReleasesTheCreatedFile() {
        var target = spy(isolatedLogger());
        var borrowed = mock(Handler.class);
        target.addHandler(borrowed);
        target.setLevel(Level.WARNING);
        var failure = new IllegalStateException("cannot attach file handler");
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof java.util.logging.FileHandler) throw failure;
            return invocation.callRealMethod();
        }).when(target).addHandler(any());
        assertThatThrownBy(() -> LoggerInitializer.initialize(directory, target, configuration())).isSameAs(failure);
        assertThat(target.getHandlers()).containsExactly(borrowed);
        assertThat(target.getLevel()).isEqualTo(Level.WARNING);
        assertThat(directory.resolve("log0.log.lck")).doesNotExist();
        verify(borrowed, never()).close();
    }

    @Test
    void aConfigurationReadFailureClosesItsStreamAndLeavesLoggingUntouched() throws Exception {
        var target = isolatedLogger();
        var failure = new IOException("cannot read logging configuration");
        var source = spy(new InputStream() { @Override public int read() throws IOException { throw failure; } });
        assertThatThrownBy(() -> LoggerInitializer.initialize(directory, target, source))
                .isInstanceOf(UncheckedIOException.class).hasCause(failure);
        verify(source).close();
        assertThat(target.getHandlers()).isEmpty();
        assertThat(directory.toFile().list()).isEmpty();
    }

    @Test
    void rotationUsesTheConfiguredLimitAndCount() throws Exception {
        var target = isolatedLogger();
        var source = new ByteArrayInputStream(("java.util.logging.FileHandler.limit=80\n"
                + "java.util.logging.FileHandler.count=2\n"
                + "java.util.logging.ConsoleHandler.level=OFF\n").getBytes(StandardCharsets.UTF_8));
        try (var session = LoggerInitializer.initialize(directory, target, source)) {
            for (int i = 0; i < 8; i++) target.info("record-" + i + "-" + "x".repeat(90));
        }
        try (var files = Files.list(directory)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactlyInAnyOrder("log0.log", "log1.log");
        }
        assertThat(Files.readString(directory.resolve("log1.log"))).contains("record-7").doesNotContain("record-0");
    }

    private static Logger isolatedLogger() {
        var target = Logger.getAnonymousLogger();
        target.setUseParentHandlers(false);
        return target;
    }

    private static InputStream configuration() {
        return LoggerInitializer.class.getResourceAsStream("/logging.properties");
    }
}
