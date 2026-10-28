package com.github.k7t3.tcv.app.secure;

import java.time.Duration;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import com.github.k7t3.tcv.lifecycle.CloseActions;

/** OS保管処理のプロセスを所有し、秘密を私有パイプで交換する。 */
final class ProcessOsSecretStore implements OsSecretStore {
    private static final Set<String> ENVIRONMENT = Set.of("SYSTEMROOT", "WINDIR", "USERPROFILE",
            "HOME", "USER", "LOGNAME", "LANG", "LC_ALL", "LC_CTYPE", "LC_MESSAGES", "APPDATA",
            "LOCALAPPDATA", "XDG_RUNTIME_DIR", "DBUS_SESSION_BUS_ADDRESS", "DISPLAY", "WAYLAND_DISPLAY",
            "XAUTHORITY", "TMPDIR", "TMP", "TEMP");
    private final Supplier<ProcessBuilder> helper;
    private final Duration timeout;

    ProcessOsSecretStore(Supplier<ProcessBuilder> helper, Duration timeout) {
        this.helper = Objects.requireNonNull(helper);
        this.timeout = Objects.requireNonNull(timeout);
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("positive helper timeout required");
    }

    @Override public byte[] read(String id) { return exchange(id, null); }

    @Override public void save(String id, byte[] secret) {
        Objects.requireNonNull(secret);
        if (secret.length != 32) throw new IllegalArgumentException("32-byte protection secret required");
        exchange(id, secret);
    }

    private byte[] exchange(String id, byte[] secret) {
        var identifier = UUID.fromString(id);
        Process process = null;
        Throwable failure = null;
        try {
            // 子の標準出力はプロトコル専用。診断へ秘密を混ぜない。
            var command = helper.get();
            command.environment().keySet().removeIf(name -> !ENVIRONMENT.contains(name.toUpperCase(Locale.ROOT)));
            process = command.redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (var output = new DataOutputStream(process.getOutputStream())) {
                output.writeInt(0x5443564b);
                output.writeByte(1);
                output.writeByte(secret == null ? 1 : 2);
                output.writeLong(identifier.getMostSignificantBits());
                output.writeLong(identifier.getLeastSignificantBits());
                if (secret != null) output.write(secret);
            }
            // 応答は最大33バイト。子の終了前にreadして期限管理を止めない。
            if (!process.waitFor(timeout.toNanos(), TimeUnit.NANOSECONDS)) {
                throw new KeyManagerException("OS secret helper timed out");
            }
            if (process.exitValue() != 0) throw new KeyManagerException("OS secret helper exited with code " + process.exitValue());
            try (var input = new DataInputStream(process.getInputStream())) {
                return response(input, secret == null);
            }
        } catch (IOException cause) {
            var wrapped = new KeyManagerException("OS secret helper communication failed", cause);
            failure = wrapped;
            throw wrapped;
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            var wrapped = new KeyManagerException("OS secret helper interrupted", cause);
            failure = wrapped;
            throw wrapped;
        } catch (RuntimeException | Error cause) {
            failure = cause;
            throw cause;
        } finally {
            if (process != null) {
                var owned = process;
                try {
                    CloseActions.runAll(() -> terminate(owned), () -> close(owned.getOutputStream()),
                            () -> close(owned.getInputStream()), () -> close(owned.getErrorStream()));
                } catch (RuntimeException | Error cleanup) {
                    if (failure != null) failure.addSuppressed(cleanup);
                    else throw cleanup;
                }
            }
        }
    }

    private static byte[] response(DataInputStream input, boolean read) throws IOException {
        var status = input.readUnsignedByte();
        if (status == 3) {
            var code = input.readInt();
            if (input.read() != -1) throw new IOException("unexpected trailing OS helper error");
            throw new KeyManagerException("OS secret operation failed with code " + code);
        }
        byte[] secret = null;
        var accepted = false;
        try {
            if (read && status == 1) {
                secret = new byte[32];
                input.readFully(secret);
            } else if (!(read && status == 0 || !read && status == 2)) {
                throw new IOException("unexpected OS helper response");
            }
            if (input.read() != -1) throw new IOException("unexpected trailing OS helper response");
            accepted = true;
            return secret;
        } finally {
            if (!accepted && secret != null) Arrays.fill(secret, (byte) 0);
        }
    }

    private static void terminate(Process process) {
        var interrupted = Thread.interrupted();
        try {
            if (process.isAlive()) process.destroyForcibly();
            while (true) {
                try { process.waitFor(); return; }
                catch (InterruptedException ignored) { interrupted = true; }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt(); }
    }

    private static void close(Closeable stream) {
        try { stream.close(); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
