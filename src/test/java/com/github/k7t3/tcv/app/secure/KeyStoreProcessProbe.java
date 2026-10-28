package com.github.k7t3.tcv.app.secure;

import javax.crypto.spec.SecretKeySpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** 別 JVM からの保存完了と生成競合を検証する。実ユーザーの鍵管理 Factory は使わない。 */
public final class KeyStoreProcessProbe {
    public static void main(String[] arguments) throws Exception {
        var path = Path.of(arguments[0]);
        var ready = Path.of(arguments[1]);
        var generated = Path.of(arguments[2]);
        var result = Path.of(arguments[3]);
        var value = Byte.parseByte(arguments[4]);
        try (var manager = new KeyManagerImpl(path, "test-password".toCharArray())) {
            var deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(ready)) {
                if (System.nanoTime() > deadline) throw new AssertionError("generation barrier timed out");
                Thread.sleep(5);
            }
            var key = manager.getOrCreateSecret("credential", () -> {
                try { Files.createFile(generated); }
                catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
                var bytes = new byte[32];
                Arrays.fill(bytes, value);
                return new SecretKeySpec(bytes, "AES");
            });
            Files.write(result, key.getEncoded());
        }
    }
}
