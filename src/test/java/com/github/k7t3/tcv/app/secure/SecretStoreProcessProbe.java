package com.github.k7t3.tcv.app.secure;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** OS保管庫の代わりにテスト専用ディレクトリを使い、プロセス間の契約を検証する。 */
public final class SecretStoreProcessProbe {
    public static void main(String[] arguments) throws Exception {
        var directory = Path.of(arguments[0]);
        var mode = arguments[1];
        if (arguments.length != 2) throw new AssertionError("unexpected helper arguments");
        var pending = directory.resolve(mode + ".starting");
        Files.writeString(pending, Long.toString(ProcessHandle.current().pid()));
        Files.move(pending, directory.resolve(mode + ".pid"), java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (mode.equals("environment") && (System.getenv("TCV_TEST_UNRELATED_SECRET") != null
                || System.getProperty("tcv.test.injected.option") != null)) {
            throw new AssertionError("unrelated environment reached the secret helper");
        }
        if (mode.equals("hang")) {
            new java.util.concurrent.CountDownLatch(1).await();
            return;
        }
        try (var input = new DataInputStream(System.in); var output = new DataOutputStream(System.out)) {
            if (input.readInt() != 0x5443564b || input.readUnsignedByte() != 1) throw new AssertionError("invalid protocol");
            var operation = input.readUnsignedByte();
            var id = new UUID(input.readLong(), input.readLong());
            var file = directory.resolve(id.toString());
            if (operation == 1) {
                if (input.read() != -1) throw new AssertionError("unexpected read input");
                if (mode.equals("denied")) { output.writeByte(3); output.writeInt(-25293); return; }
                if (mode.equals("short")) { output.writeByte(1); output.write(new byte[31]); return; }
                if (mode.equals("extra")) { output.writeByte(1); output.write(new byte[33]); return; }
                if (mode.equals("wrong-status")) { output.writeByte(2); return; }
                if (mode.equals("exit")) { System.exit(7); return; }
                if (Files.exists(file)) {
                    output.writeByte(1); output.write(Files.readAllBytes(file));
                } else output.writeByte(0);
            } else if (operation == 2) {
                var secret = input.readNBytes(32);
                if (secret.length != 32 || input.read() != -1) throw new AssertionError("invalid write input");
                if (mode.equals("denied")) { output.writeByte(3); output.writeInt(-25293); return; }
                Files.write(file, secret);
                output.writeByte(2);
            } else throw new AssertionError("invalid operation");
        }
    }
}
