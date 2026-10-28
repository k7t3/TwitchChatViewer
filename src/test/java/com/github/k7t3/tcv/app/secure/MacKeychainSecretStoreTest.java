package com.github.k7t3.tcv.app.secure;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledOnOs(OS.MAC)
@Tag("os-keychain")
class MacKeychainSecretStoreTest {
    @TempDir Path directory;

    @Test
    void separateProcessesUseOnlyTheDedicatedKeychainAndReopenProtectedKeys() throws Exception {
        var keychain = directory.resolve("tcv-test.keychain-db");
        try {
            fixture("create", keychain);
            var secrets = new ProcessOsSecretStore(() -> command("serve", keychain), Duration.ofSeconds(10));
            var id = UUID.randomUUID().toString();
            var value = new byte[32]; Arrays.fill(value, (byte) 7);

            assertThat(secrets.read(id)).isNull();
            secrets.save(id, value);
            assertThat(secrets.read(id)).containsExactly(value);
            var path = directory.resolve("keys.ks");
            try (var writer = new KeyManagerImpl(path, secrets)) {
                writer.store("credential", new SecretKeySpec(value, "AES"));
            }
            try (var reader = new KeyManagerImpl(path, secrets)) {
                assertThat(reader.getSecret("credential").getEncoded()).containsExactly(value);
            }
        } finally { fixture("delete", keychain); }

        assertThat(keychain).doesNotExist();
        assertThat(directory.resolve("keychain-isolation.ok")).hasContent("search list preserved");
    }

    private void fixture(String mode, Path keychain) throws Exception {
        var log = directory.resolve(mode + ".log");
        var process = command(mode, keychain).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).as("keychain fixture " + mode).isTrue();
            assertThat(process.exitValue()).withFailMessage(Files.readString(log)).isZero();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
            assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private ProcessBuilder command(String mode, Path keychain) {
        try {
            var main = Path.of(KeyManagerImpl.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            var tests = Path.of(MacKeychainProcessProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", main + java.io.File.pathSeparator + tests,
                    MacKeychainProcessProbe.class.getName(), mode, keychain.toString());
        } catch (java.net.URISyntaxException failure) { throw new AssertionError(failure); }
    }
}
