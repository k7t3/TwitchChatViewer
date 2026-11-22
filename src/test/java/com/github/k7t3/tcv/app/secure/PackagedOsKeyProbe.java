package com.github.k7t3.tcv.app.secure;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/** 隔離した配布コピーと専用キーチェーンだけで、既定Factoryからの実経路を検証する。 */
public final class PackagedOsKeyProbe {
    /** @param arguments 合成データの保存先。実際のユーザー設定は使わない。 */
    public static void main(String[] arguments) throws Exception {
        var root = Path.of(arguments[0]);
        var path = root.resolve("synthetic.keys");
        System.setProperty("key.manager.path", path.toString());
        System.clearProperty("key.manager.password");
        var result = new Properties();
        try {
            var launcher = System.getProperty("jpackage.app-path");
            check(launcher != null, "nativeLauncherProperty", result);
            var helper = Path.of(launcher).resolveSibling("Twitch Key Storage");
            check(SecretStoreCommand.current().command().equals(java.util.List.of(helper.toString())), "ownedHelperResolved", result);
            check(!Files.exists(Path.of(System.getProperty("java.home"), "bin", "java")), "runtimeHasNoJavaCommand", result);
            var plain = "synthetic-package-key-fixture".getBytes(StandardCharsets.UTF_8);
            byte[] encrypted;
            try (var cipher = KeyManagerFactory.create()) { encrypted = cipher.encrypt(plain); }
            var file = Files.readAllBytes(path);
            check(file.length > 24 && Arrays.equals(Arrays.copyOf(file, 8), new byte[] {'T', 'C', 'V', 'K', 'E', 'Y', 'S', 2}),
                    "defaultFactoryUsesOsProtection", result);
            try (var reopened = KeyManagerFactory.create()) {
                check(Arrays.equals(reopened.decrypt(encrypted), plain), "separateServiceReopened", result);
            }
        } finally {
            try (var output = Files.newOutputStream(root.resolve("probe.properties"))) {
                result.store(output, "synthetic packaged OS-key probe");
            }
        }
    }

    private static void check(boolean value, String name, Properties result) {
        result.setProperty(name, Boolean.toString(value));
        if (!value) throw new AssertionError(name);
    }
}
