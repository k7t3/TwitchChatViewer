package com.github.k7t3.tcv.app.secure;

import java.nio.file.Path;
import java.nio.file.Files;
import java.net.URISyntaxException;
import java.util.Locale;

/** 配布済みアプリと開発用JDKのヘルパー起動経路を解決する。 */
final class SecretStoreCommand {
    private SecretStoreCommand() {}

    static ProcessBuilder current() {
        try {
            var application = Path.of(SecretStoreHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            var launcher = System.getProperty("jpackage.app-path");
            return create(launcher == null ? null : Path.of(launcher), Path.of(System.getProperty("java.home")), application,
                    System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows"));
        } catch (URISyntaxException failure) { throw new KeyManagerException("invalid application code source", failure); }
    }

    static ProcessBuilder create(Path launcher, Path javaHome, Path application, boolean windows) {
        if (launcher != null)
            return new ProcessBuilder(executable(launcher.resolveSibling("Twitch Key Storage" + (windows ? ".exe" : ""))));
        return new ProcessBuilder(executable(javaHome.resolve("bin").resolve(windows ? "java.exe" : "java")),
                "--enable-native-access=ALL-UNNAMED", "-cp", application.toString(), SecretStoreHelper.class.getName());
    }

    private static String executable(Path path) {
        if (!Files.isRegularFile(path) || !Files.isExecutable(path))
            throw new KeyManagerException("missing OS secret helper executable");
        return path.toString();
    }
}
