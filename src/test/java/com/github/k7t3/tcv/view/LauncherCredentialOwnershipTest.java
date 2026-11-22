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

class LauncherCredentialOwnershipTest {
    @TempDir Path directory;

    @Test
    void storedCredentialsCanBeReadByTheNextLauncherAfterThePreviousOneStops() throws Exception {
        runProbe(null, "stored credentials survived two launcher lifetimes");
    }

    @Test
    void shutdownWaitsForTheAcceptedCredentialReadBeforeClosingItsKeys() throws Exception {
        runProbe("blocked-close", "accepted credential read finished before key shutdown");
    }

    @Test
    void theProductionLauncherUsesProcessExclusionWhenReadingCredentials() throws Exception {
        runProbe("process-lock", "production credential read waited for the independent process lock");
    }

    @Test
    void theProductionCredentialReadAlsoWaitsForAnIndependentSettingsWriter() throws Exception {
        runProbe("shared-settings-lock", "production credential read waited for the shared settings process lock");
    }

    @Test
    void shutdownCanCancelAKeyLockWaitOnTheFxThreadWithoutTheLockOwnerExiting() throws Exception {
        runProbe("key-lock-cancel", "FX shutdown cancelled key lock wait before independent lock release");
    }

    private void runProbe(String mode, String expected) throws Exception {
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
        command.add(LauncherCredentialOwnershipProbe.class.getName());
        command.add(directory.toString());
        if (mode != null) command.add(mode);
        var log = directory.resolve("startup.log");
        // 既定の保存先・サービスとPlatform.exitを別JVMへ隔離する。
        var process = TestJvm.utf8(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(45, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
            assertThat(Files.readString(log)).contains(expected);
        } finally { process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
    }
}
