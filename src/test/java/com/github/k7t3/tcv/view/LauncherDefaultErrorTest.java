package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.support.TestJvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class LauncherDefaultErrorTest {
    @TempDir Path directory;

    @Test
    void aFailureBeforeCreatingAnApplicationDoesNotAcquireAnotherApplicationsWindow() throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("--enable-native-access=ALL-UNNAMED");
        command.add("-javaagent:" + Path.of(Mockito.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        for (var name : new String[] {"glass.platform", "java.awt.headless", "prism.order"}) {
            var value = System.getProperty(name);
            if (value != null) command.add("-D" + name + "=" + value);
        }
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(LauncherDefaultErrorProbe.class.getName());
        var log = directory.resolve("startup.log");
        // 既定Launcherの鍵サービス終了とPlatform.exitは、別JVMの範囲へ閉じ込める。
        var process = TestJvm.utf8(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
            assertThat(Files.readString(log)).contains("startup failure remained unowned without acquiring another application");
        } finally { process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
    }
}
