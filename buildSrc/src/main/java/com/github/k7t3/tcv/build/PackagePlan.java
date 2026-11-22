package com.github.k7t3.tcv.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** jpackage の引数を不変の入力から組み立てる。外部コマンドや既存の配布物には作用しない。 */
public final class PackagePlan {
    private static final Pattern VERSION = Pattern.compile("([0-9]+)\\.([0-9]+)\\.([0-9]+)(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?");
    private static final Pattern MODULE = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");

    private PackagePlan() {}

    public enum Platform {
        WINDOWS("exe", "TwitchChatViewer.ico"), MAC("dmg", "TwitchChatViewer.icns"), LINUX("deb", "TwitchChatViewer-64.png");
        private final String installer;
        private final String icon;
        Platform(String installer, String icon) { this.installer = installer; this.icon = icon; }
        public String installerType() { return installer; }
        public String iconName() { return icon; }
        public static Platform fromOsName(String name) {
            var value = name.toLowerCase(Locale.ROOT);
            if (value.startsWith("windows")) return WINDOWS;
            if (value.startsWith("mac")) return MAC;
            if (value.startsWith("linux")) return LINUX;
            throw new IllegalArgumentException("unsupported packaging OS: " + name);
        }
    }

    public record Inputs(Platform platform, boolean appImage, String fullVersion, Path inputDirectory, Path moduleDirectory,
                         String mainJar, String mainClass, Path projectRoot, List<String> modules) {
        public Inputs { modules = List.copyOf(modules); }
    }

    public static String nativeVersion(String version) {
        var matcher = VERSION.matcher(version == null ? "" : version);
        if (!matcher.matches()) throw new IllegalArgumentException("invalid application version: " + version);
        return matcher.group(1) + "." + matcher.group(2) + "." + matcher.group(3);
    }

    public static List<String> modules(String output, List<String> javafx) {
        if (output == null || output.isBlank()) throw new IllegalArgumentException("jdeps returned no modules");
        var modules = new TreeSet<String>();
        for (var name : output.trim().split(",", -1)) {
            if (!MODULE.matcher(name).matches()) throw new IllegalArgumentException("invalid jdeps module: " + name);
            modules.add(name);
        }
        for (var name : javafx) {
            if (!MODULE.matcher(name).matches()) throw new IllegalArgumentException("invalid javafx module: " + name);
            modules.add(name);
        }
        modules.add("jdk.crypto.ec");
        return List.copyOf(modules);
    }

    public static void validateInputs(Inputs input) {
        nativeVersion(input.fullVersion());
        var jar = input.mainJar();
        if (jar == null || jar.isBlank() || jar.contains("/") || jar.contains("\\") || jar.equals(".") || jar.equals(".."))
            throw new IllegalArgumentException("main JAR must be an input file name");
        if (!Files.isRegularFile(input.inputDirectory().resolve(jar))) throw new IllegalArgumentException("missing main JAR: " + jar);
        if (!Files.isRegularFile(input.inputDirectory().resolve("key-storage.properties")))
            throw new IllegalArgumentException("missing key storage launcher configuration");
        if (!Files.isDirectory(input.moduleDirectory())) throw new IllegalArgumentException("missing JavaFX module directory");
        if (!Files.isRegularFile(input.projectRoot().resolve("docs/license.md"))) throw new IllegalArgumentException("missing package license");
        if (!Files.isRegularFile(icon(input))) throw new IllegalArgumentException("missing package icon: " + icon(input));
        modules(String.join(",", input.modules()), List.of());
    }

    private static Path icon(Inputs input) { return input.projectRoot().resolve("src/main/resources/icons").resolve(input.platform().iconName()); }

    /**
     * 検証済みの入力から引数を組み立て、Linuxインストーラーには対象JavaFXのGLIBC要件も加える。
     * @param input 配布対象と入力ファイル
     * @param output 自己所有の生成先
     * @return 呼出側が変更できない引数一覧
     * @throws IOException Linuxネイティブ資源の読取・ABI解析に失敗した場合
     */
    public static List<String> arguments(Inputs input, Path output) throws IOException {
        validateInputs(input);
        var args = new ArrayList<>(List.of(
                "--verbose", "--type", input.appImage() ? "app-image" : input.platform().installerType(),
                "--name", "Twitch Chat Viewer", "--description", "Chat Viewer for twitch.tv",
                "--copyright", "Copyright (c) 2024 k7t3. All rights reserved.", "--vendor", "k7t3",
                "--app-version", nativeVersion(input.fullVersion()), "--dest", output.toString(),
                "--module-path", input.moduleDirectory().toString(), "--add-modules", String.join(",", modules(String.join(",", input.modules()), List.of())),
                "--input", input.inputDirectory().toString(), "--main-jar", input.mainJar(), "--main-class", input.mainClass(),
                "--add-launcher", "Twitch Key Storage=" + input.inputDirectory().resolve("key-storage.properties"),
                "--java-options", "-Dapp.version=" + input.fullVersion(),
                "--java-options", "--enable-native-access=javafx.graphics,ALL-UNNAMED", "--icon", icon(input).toString()));
        if (input.platform() == Platform.MAC) args.addAll(List.of("--mac-package-identifier", "TwitchChatViewer"));
        if (!input.appImage()) {
            args.addAll(List.of("--license-file", input.projectRoot().resolve("docs/license.md").toString()));
            switch (input.platform()) {
                case WINDOWS -> args.addAll(List.of("--win-shortcut", "--win-menu", "--win-menu-group", "TwitchChatViewer", "--win-per-user-install",
                        "--win-shortcut-prompt", "--win-upgrade-uuid", "b78817e8-f9b8-4650-bf0a-34bca5ff4106", "--win-help-url",
                        "https://github.com/k7t3/TwitchChatViewer", "--win-update-url", "https://github.com/k7t3/TwitchChatViewer/releases"));
                case LINUX -> {
                    var dependencies = new ArrayList<>(List.of("libsecret-1-0 (>= 0.19.0)", "libgtk-3-0 (>= 3.20.0)", "libxtst6"));
                    LinuxGlibcRequirement.minimumVersion(input.moduleDirectory())
                            .ifPresent(version -> dependencies.add("libc6 (>= " + version + ")"));
                    args.addAll(List.of("--linux-menu-group", "Network", "--linux-rpm-license-type", "Apache-2.0", "--linux-shortcut",
                            "--linux-package-deps", String.join(", ", dependencies)));
                }
                case MAC -> {}
            }
        }
        return List.copyOf(args);
    }
}
