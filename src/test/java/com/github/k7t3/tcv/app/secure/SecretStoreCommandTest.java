package com.github.k7t3.tcv.app.secure;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretStoreCommandTest {
    @TempDir Path directory;

    @Test
    void aJpackageInvocationUsesItsOwnSiblingHelperInsteadOfTheRunningJdk() throws Exception {
        var previous = System.getProperty("jpackage.app-path");
        var windows = System.getProperty("os.name").startsWith("Windows");
        var bin = Files.createDirectories(directory.resolve("packaged app with spaces"));
        var launcher = Files.writeString(bin.resolve("Twitch Chat Viewer" + (windows ? ".exe" : "")), "launcher");
        var helper = Files.writeString(bin.resolve("Twitch Key Storage" + (windows ? ".exe" : "")), "helper");
        assertThat(helper.toFile().setExecutable(true)).isTrue();
        try {
            System.setProperty("jpackage.app-path", launcher.toString());

            assertThat(SecretStoreCommand.current().command()).containsExactly(helper.toString());
        } finally {
            if (previous == null) System.clearProperty("jpackage.app-path");
            else System.setProperty("jpackage.app-path", previous);
        }
    }

    @Test
    void aDevelopmentInvocationUsesItsCurrentJdkAndOwnCodeSource() throws Exception {
        var previous = System.getProperty("jpackage.app-path");
        try {
            System.clearProperty("jpackage.app-path");
            var windows = System.getProperty("os.name").startsWith("Windows");
            var classes = Path.of(SecretStoreHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());

            var command = SecretStoreCommand.current();

            assertThat(command.command()).containsExactly(Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", classes.toString(), SecretStoreHelper.class.getName());
        } finally {
            if (previous == null) System.clearProperty("jpackage.app-path");
            else System.setProperty("jpackage.app-path", previous);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aMissingOwnedExecutableFailsWithoutSearchingTheSystemPath(boolean packaged) throws Exception {
        var home = directory.resolve("runtime");
        var application = Files.createDirectories(directory.resolve("application"));
        var launcher = packaged ? application.resolve("Twitch Chat Viewer") : null;
        if (packaged) {
            var java = Files.writeString(Files.createDirectories(home.resolve("bin")).resolve("java"), "unrelated JDK");
            assertThat(java.toFile().setExecutable(true)).isTrue();
        }

        assertThatThrownBy(() -> SecretStoreCommand.create(launcher, home, application, false))
                .isInstanceOf(KeyManagerException.class).hasMessageContaining("executable");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void developmentUsesTheExplicitJdkAndOnlyTheApplicationsOwnClasses(boolean windows) throws Exception {
        var home = directory.resolve("JDK with spaces");
        var bin = Files.createDirectories(home.resolve("bin"));
        var java = Files.writeString(bin.resolve(windows ? "java.exe" : "java"), "java");
        assertThat(java.toFile().setExecutable(true)).isTrue();
        var application = Files.createDirectories(directory.resolve("application classes"));

        var command = SecretStoreCommand.create(null, home, application, windows);

        assertThat(command.command()).containsExactly(java.toString(), "--enable-native-access=ALL-UNNAMED",
                "-cp", application.toString(), "com.github.k7t3.tcv.app.secure.SecretStoreHelper");
    }

    @ParameterizedTest
    @CsvSource({"mac,Bundle with spaces.app/Contents/MacOS,false", "linux,application with spaces/bin,false", "windows,application with spaces,true"})
    void aPackagedApplicationUsesItsSiblingHelperWithoutRequiringAJdk(String os, String layout, boolean windows) throws Exception {
        var bin = Files.createDirectories(directory.resolve(layout));
        var launcher = Files.writeString(bin.resolve("Twitch Chat Viewer" + (windows ? ".exe" : "")), "launcher");
        var helper = Files.writeString(bin.resolve("Twitch Key Storage" + (windows ? ".exe" : "")), "helper");
        assertThat(helper.toFile().setExecutable(true)).isTrue();

        var command = SecretStoreCommand.create(launcher, directory.resolve("runtime without bin"),
                directory.resolve("application.jar"), windows);

        assertThat(command.command()).containsExactly(helper.toString());
    }
}
