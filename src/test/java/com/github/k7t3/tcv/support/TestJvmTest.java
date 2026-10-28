package com.github.k7t3.tcv.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class TestJvmTest {
    @TempDir Path directory;

    @Test
    void capturedOutputUsesUtf8ForBothStreamsAndKeepsJvmArguments() throws Exception {
        var output = directory.resolve("stdout.log");
        var errors = directory.resolve("stderr.log");
        var process = TestJvm.utf8(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Dtcv.fixture.argument=survives", "-cp", System.getProperty("java.class.path"),
                UnicodeProbe.class.getName()).redirectOutput(output.toFile()).redirectError(errors.toFile()).start();
        try {
            process.getOutputStream().close();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            assertThat(Files.readString(output)).isEqualTo("標準出力🙂:survives");
            assertThat(Files.readString(errors)).isEqualTo("標準エラー🙂");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    public static final class UnicodeProbe {
        public static void main(String[] arguments) {
            System.out.print("標準出力🙂:" + System.getProperty("tcv.fixture.argument"));
            System.err.print("標準エラー🙂");
        }
    }
}
