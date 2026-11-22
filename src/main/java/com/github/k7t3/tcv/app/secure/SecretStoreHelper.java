package com.github.k7t3.tcv.app.secure;

import java.io.IOException;
import java.util.Locale;

/** JavaFXを起動せず、一回のOS保管要求を処理して終了する。 */
public final class SecretStoreHelper {
    private SecretStoreHelper() {}

    /** 引数から秘密を受け取らず、私有標準入出力だけを使用する。 */
    public static void main(String[] args) {
        try {
            if (args.length != 0) throw new IOException("unexpected OS helper arguments");
            SecretStoreProtocol.serve(System.in, System.out, () -> nativeStore(
                    System.getProperty("os.name", ""), System.getenv("DBUS_SESSION_BUS_ADDRESS")));
        } catch (IOException | RuntimeException failure) {
            // OSの診断文や入力データを標準出力・標準エラーへ転記しない。
            System.exit(2);
        }
    }

    static OsSecretStore nativeStore(String name, String sessionBus) {
        var os = name.toLowerCase(Locale.ROOT);
        if (os.startsWith("mac")) return new MacKeychainSecretStore("com.github.k7t3.tcv.key-protection", null);
        if (os.startsWith("windows")) return new WindowsCredentialSecretStore();
        if (os.startsWith("linux")) {
            if (sessionBus == null || sessionBus.isBlank() || sessionBus.contains("autolaunch:"))
                throw new NativeSecretStoreException(-1);
            return new LibsecretSecretStore();
        }
        throw new NativeSecretStoreException(-1);
    }
}
