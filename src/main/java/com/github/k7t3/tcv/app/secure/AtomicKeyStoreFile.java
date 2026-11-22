package com.github.k7t3.tcv.app.secure;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;

/** 完成して強制書込済みのファイルだけを公開する。非原子的な置換へはフォールバックしない。 */
final class AtomicKeyStoreFile {
    @FunctionalInterface interface Writer { void write(Path path, byte[] bytes) throws IOException; }
    @FunctionalInterface interface Commit { void replace(Path temporary, Path target) throws IOException; }
    private final Writer writer;
    private final Commit commit;

    AtomicKeyStoreFile() { this(AtomicKeyStoreFile::writeAndForce, AtomicKeyStoreFile::replace); }
    AtomicKeyStoreFile(Writer writer, Commit commit) {
        this.writer = writer;
        this.commit = commit;
    }

    void write(Path target, byte[] bytes) throws IOException {
        var temporary = Files.createTempFile(target.getParent(), ".tcv-keys-", ".tmp", permissions(target, "rw-------"));
        Throwable failure = null;
        try {
            writer.write(temporary, bytes);
            commit.replace(temporary, target);
            // POSIX では置換先のディレクトリエントリーも強制書込する。
            if (target.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                try (var parent = FileChannel.open(target.getParent(), StandardOpenOption.READ)) { parent.force(true); }
            }
        } catch (IOException | RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            try { Files.deleteIfExists(temporary); }
            catch (IOException cleanupFailure) {
                if (failure != null) failure.addSuppressed(cleanupFailure);
                else throw cleanupFailure;
            }
        }
    }

    static void writeAndForce(Path temporary, byte[] bytes) throws IOException {
        try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    static void replace(Path temporary, Path target) throws IOException {
        Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    static FileAttribute<?>[] permissions(Path path, String mode) {
        if (!path.getFileSystem().supportedFileAttributeViews().contains("posix")) return new FileAttribute<?>[0];
        return new FileAttribute<?>[]{PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(mode))};
    }
}
