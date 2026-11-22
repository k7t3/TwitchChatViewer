package com.github.k7t3.tcv.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import static org.assertj.core.api.Assertions.*;

class PackagePlanTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void linuxInstallersRequireTheBinarySecretServiceLibrary(boolean image) throws Exception {
        var arguments = PackagePlan.arguments(input(PackagePlan.Platform.LINUX, image), directory.resolve("package output"));

        if (image) assertThat(arguments).doesNotContain("--linux-package-deps");
        else {
            assertThat(arguments).contains("--linux-package-deps");
            assertThat(arguments.get(arguments.indexOf("--linux-package-deps") + 1))
                    .contains("libsecret-1-0 (>= 0.19.0)");
        }
    }

    @Test
    void linuxInstallersRequireTheGtkRuntimeUsedByJavafx() throws Exception {
        var arguments = PackagePlan.arguments(input(PackagePlan.Platform.LINUX, false), directory.resolve("package output"));

        assertThat(arguments).contains("--linux-package-deps");
        assertThat(arguments.get(arguments.indexOf("--linux-package-deps") + 1))
                .contains("libgtk-3-0 (>= 3.20.0)");
    }

    @Test
    void linuxInstallersRequireTheXTestLibraryLoadedByJavafx() throws Exception {
        var arguments = PackagePlan.arguments(input(PackagePlan.Platform.LINUX, false), directory.resolve("package output"));

        assertThat(arguments).contains("--linux-package-deps");
        assertThat(arguments.get(arguments.indexOf("--linux-package-deps") + 1))
                .contains("libxtst6");
    }

    @ParameterizedTest
    @CsvSource({"WINDOWS,true", "WINDOWS,false", "MAC,true", "MAC,false", "LINUX,true", "LINUX,false"})
    void packagesIncludeASeparateSecretStorageLauncher(PackagePlan.Platform platform, boolean image) throws Exception {
        var input = input(platform, image);

        var arguments = PackagePlan.arguments(input, directory.resolve("package output"));

        assertThat(arguments).containsSequence("--add-launcher", "Twitch Key Storage="
                + input.inputDirectory().resolve("key-storage.properties"));
    }

    @ParameterizedTest
    @CsvSource({"WINDOWS,true", "WINDOWS,false", "MAC,true", "MAC,false", "LINUX,true", "LINUX,false"})
    void launchersEnableNativeAccessForJavafxAndClasspathLibraries(PackagePlan.Platform platform, boolean image) throws Exception {
        assertThat(PackagePlan.arguments(input(platform, image), directory.resolve("package output")))
                .containsSequence("--java-options", "--enable-native-access=javafx.graphics,ALL-UNNAMED");
    }

    @ParameterizedTest @CsvSource({"WINDOWS,exe,TwitchChatViewer.ico", "MAC,dmg,TwitchChatViewer.icns", "LINUX,deb,TwitchChatViewer-64.png"})
    void nativePackagesKeepTheirPlatformOptionsAndPassUnquotedArguments(PackagePlan.Platform platform, String type, String icon) throws Exception {
        var input = input(platform, false); var output = directory.resolve("native output");
        var args = PackagePlan.arguments(input, output);
        assertThat(args).containsSequence("--type", type).containsSequence("--app-version", "2.3.4")
                .containsSequence("--java-options", "-Dapp.version=2.3.4-beta.2+42")
                .containsSequence("--input", input.inputDirectory().toString()).containsSequence("--dest", output.toString())
                .containsSequence("--main-jar", "tcv-2.3.4-beta.2+42.jar")
                .containsSequence("--icon", input.projectRoot().resolve("src/main/resources/icons").resolve(icon).toString())
                .containsSequence("--license-file", input.projectRoot().resolve("docs/license.md").toString());
        assertThat(args.stream().noneMatch(argument -> argument.startsWith("'") || argument.startsWith("\""))).isTrue();
        switch (platform) {
            case WINDOWS -> assertThat(args).containsSequence("--win-upgrade-uuid", "b78817e8-f9b8-4650-bf0a-34bca5ff4106").contains("--win-per-user-install");
            case MAC -> assertThat(args).containsSequence("--mac-package-identifier", "TwitchChatViewer");
            case LINUX -> assertThat(args).containsSequence("--linux-menu-group", "Network").contains("--linux-shortcut");
        }
    }

    @ParameterizedTest @ValueSource(strings = {"WINDOWS", "MAC", "LINUX"})
    void applicationImagesUseLauncherOptionsAndOmitInstallerOnlyOptions(PackagePlan.Platform platform) throws Exception {
        var args = PackagePlan.arguments(input(platform, true), directory.resolve("app image"));
        assertThat(args).containsSequence("--type", "app-image").doesNotContain("--license-file", "--win-upgrade-uuid", "--win-menu", "--linux-shortcut");
        if (platform == PackagePlan.Platform.MAC) assertThat(args).containsSequence("--mac-package-identifier", "TwitchChatViewer");
    }

    @ParameterizedTest @CsvSource({"1.0.0-dev,1.0.0", "2.3.4-rc.2+build.9,2.3.4", "2.3.4+build.9,2.3.4", "3.0.1,3.0.1"})
    void nativeVersionsRemoveQualifiersIncludingThoseEndingInDigits(String full, String expected) {
        assertThat(PackagePlan.nativeVersion(full)).isEqualTo(expected);
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"v1.2.3", "1.2", "../1.2.3", "1.2.3$(command)", "1.2.3\n--type", "1.2.3-"})
    void malformedVersionsAreRejectedBeforePackaging(String version) {
        assertThatThrownBy(() -> PackagePlan.nativeVersion(version)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
    }

    @Test
    void dependencyModulesAreDeduplicatedAndKeepCryptoAndJavafxRoots() {
        assertThat(PackagePlan.modules("java.desktop,java.base,java.desktop\n", List.of("javafx.controls", "javafx.fxml", "javafx.swing")))
                .containsExactly("java.base", "java.desktop", "javafx.controls", "javafx.fxml", "javafx.swing", "jdk.crypto.ec");
    }

    @ParameterizedTest @ValueSource(strings = {"", " ", "Warning: missing dependencies\njava.base", "java.base,--add-modules"})
    void malformedDependencyOutputCannotBecomeLauncherArguments(String modules) {
        assertThatThrownBy(() -> PackagePlan.modules(modules, List.of("javafx.controls"))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("jdeps");
    }

    @Test
    void missingInputsAndEscapingJarNamesAreRejected() throws Exception {
        var input = input(PackagePlan.Platform.MAC, false); Files.delete(input.inputDirectory().resolve(input.mainJar()));
        assertThatThrownBy(() -> PackagePlan.validateInputs(input)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("main JAR");
        var escaped = new PackagePlan.Inputs(input.platform(), false, input.fullVersion(), input.inputDirectory(), input.moduleDirectory(),
                "../outside.jar", input.mainClass(), input.projectRoot(), input.modules());
        assertThatThrownBy(() -> PackagePlan.arguments(escaped, directory)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("main JAR");
    }

    @Test
    void aMissingHelperConfigurationCannotProduceAPackageWithoutItsStorageLauncher() throws Exception {
        var input = input(PackagePlan.Platform.MAC, true);
        Files.delete(input.inputDirectory().resolve("key-storage.properties"));

        assertThatThrownBy(() -> PackagePlan.arguments(input, directory.resolve("package output")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("key storage launcher");
    }

    @Test
    void platformDetectionDoesNotDependOnTheUsersLocaleAndRejectsUnknownSystems() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(PackagePlan.Platform.fromOsName("WINDOWS 11")).isEqualTo(PackagePlan.Platform.WINDOWS);
            assertThat(PackagePlan.Platform.fromOsName("Mac OS X")).isEqualTo(PackagePlan.Platform.MAC);
            assertThat(PackagePlan.Platform.fromOsName("Linux")).isEqualTo(PackagePlan.Platform.LINUX);
            assertThatThrownBy(() -> PackagePlan.Platform.fromOsName("Plan 9")).isInstanceOf(IllegalArgumentException.class);
        } finally { Locale.setDefault(previous); }
    }

    private PackagePlan.Inputs input(PackagePlan.Platform platform, boolean appImage) throws Exception {
        var root = directory.resolve("project with spaces"); var input = root.resolve("input"); var modules = root.resolve("javafx");
        Files.createDirectories(input); Files.createDirectories(modules); Files.createDirectories(root.resolve("docs"));
        Files.createDirectories(root.resolve("src/main/resources/icons"));
        Files.writeString(input.resolve("tcv-2.3.4-beta.2+42.jar"), "application"); Files.writeString(root.resolve("docs/license.md"), "license");
        Files.writeString(input.resolve("key-storage.properties"), "main-class=com.github.k7t3.tcv.app.secure.SecretStoreHelper\n");
        for (var icon : List.of("TwitchChatViewer.ico", "TwitchChatViewer.icns", "TwitchChatViewer-64.png")) Files.writeString(root.resolve("src/main/resources/icons").resolve(icon), "icon");
        return new PackagePlan.Inputs(platform, appImage, "2.3.4-beta.2+42", input, modules, "tcv-2.3.4-beta.2+42.jar",
                "com.github.k7t3.tcv.view.Launcher", root, List.of("java.base", "javafx.controls", "jdk.crypto.ec"));
    }
}
