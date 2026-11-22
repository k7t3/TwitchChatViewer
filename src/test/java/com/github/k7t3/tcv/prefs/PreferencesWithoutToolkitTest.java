package com.github.k7t3.tcv.prefs;

import com.github.k7t3.tcv.support.TestJvm;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class PreferencesWithoutToolkitTest {
    @TempDir Path directory;

    @Test void theBackgroundSaveAndFontCodecDoNotRequireAGraphicsToolkit() throws Exception {
        var classpath = System.getProperty("java.class.path");
        var log = directory.resolve("preferences.log");
        var process = TestJvm.utf8(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, PreferenceProcessProbe.class.getName())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
            assertThat(Files.readString(log)).contains("settings saved without starting JavaFX");
        } finally { process.destroyForcibly().waitFor(5, TimeUnit.SECONDS); }
    }
}
