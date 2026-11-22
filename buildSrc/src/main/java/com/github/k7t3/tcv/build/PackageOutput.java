package com.github.k7t3.tcv.build;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** 公開前の失敗では直前の成果物を残し、公開後の削除失敗は公開結果に付記する。 */
public final class PackageOutput {
    private PackageOutput() {}
    @FunctionalInterface public interface Generator { void generate(Path staging) throws Exception; }
    @FunctionalInterface interface Mover { void move(Path source, Path target) throws IOException; }
    @FunctionalInterface interface Remover { void remove(Path path) throws IOException; }

    /** 公開済み出力と、呼出側が報告する削除失敗。 */
    public record Result(Path output, List<CleanupFailure> cleanupFailures) {
        public Result { Objects.requireNonNull(output); cleanupFailures = List.copyOf(cleanupFailures); }
    }

    /** 削除できず残った自己所有領域と元のI/O失敗。 */
    public record CleanupFailure(Path directory, IOException failure) {
        public CleanupFailure { Objects.requireNonNull(directory); Objects.requireNonNull(failure); }
    }

    public static Result produce(Path output, Generator generator) throws Exception { return produce(output, generator, PackageOutput::move); }

    static Result produce(Path output, Generator generator, Mover mover) throws Exception {
        return produce(output, generator, mover, PackageOutput::deleteTree);
    }

    static Result produce(Path output, Generator generator, Mover mover, Remover remover) throws Exception {
        var destination = output.toAbsolutePath().normalize();
        if (destination.getParent() == null || (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)
                && !Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException("package output must be an owned directory: " + destination);
        }
        Files.createDirectories(destination.getParent());
        var staging = Files.createTempDirectory(destination.getParent(), ".tcv-package-");
        var cleanupFailures = new ArrayList<CleanupFailure>();
        boolean published = false;
        Throwable failure = null;
        try {
            generator.generate(staging);
            try (var generated = Files.list(staging)) {
                if (generated.findAny().isEmpty()) throw new IOException("generator produced no package");
            }
            publish(staging, destination, mover, remover, cleanupFailures);
            published = true;
        } catch (Exception | Error original) {
            failure = original;
            throw original;
        } finally {
            try { remover.remove(staging); }
            catch (IOException cleanup) {
                if (failure != null) failure.addSuppressed(cleanup);
                else if (published) cleanupFailures.add(new CleanupFailure(staging, cleanup));
                else throw cleanup;
            }
        }
        return new Result(destination, cleanupFailures);
    }

    private static void publish(Path staging, Path destination, Mover mover, Remover remover,
                                List<CleanupFailure> cleanupFailures) throws IOException {
        var backup = destination.getParent().resolve(".tcv-previous-" + UUID.randomUUID());
        var previous = Files.exists(destination, LinkOption.NOFOLLOW_LINKS);
        if (previous) mover.move(destination, backup);
        try { mover.move(staging, destination); }
        catch (IOException | RuntimeException | Error original) {
            if (previous) {
                try { mover.move(backup, destination); }
                catch (IOException | RuntimeException | Error restore) {
                    if (restore != original) original.addSuppressed(restore);
                    original.addSuppressed(new IOException("Previous package remains at " + backup));
                }
            }
            throw original;
        }
        if (previous) {
            try { remover.remove(backup); }
            catch (IOException cleanup) { cleanupFailures.add(new CleanupFailure(backup, cleanup)); }
        }
    }

    private static void move(Path source, Path target) throws IOException {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(source, target); }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                if (failure != null) throw failure;
                Files.delete(directory); return FileVisitResult.CONTINUE;
            }
        });
    }
}
