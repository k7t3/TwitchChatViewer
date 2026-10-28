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

class LauncherServiceOwnershipTest {
    @TempDir Path directory;

    @Test
    void defaultLaunchersCanCreateAndReleaseTheirOwnApplicationServices() throws Exception {
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
        command.add(LauncherServiceOwnershipProbe.class.getName());
        command.add(directory.toString());
        var log = directory.resolve("startup.log");
        // 既定の保存先・サービスとPlatform.exitを別JVMへ隔離する。
        var process = TestJvm.utf8(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
            assertThat(Files.readString(log)).contains("two default launchers released their own application services");
        } finally { process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
    }
}
