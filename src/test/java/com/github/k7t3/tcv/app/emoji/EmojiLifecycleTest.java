package com.github.k7t3.tcv.app.emoji;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmojiLifecycleTest {

    @TempDir
    Path directory;

    @Test
    void unchangedArchiveWithoutAFileKeyKeepsItsOpenedImageUntilClose() throws Exception {
        try (var emoji = new Emoji(directory, java.nio.file.Files::newInputStream,
                path -> new java.util.zip.ZipFile(path.toFile()),
                () -> Emoji.class.getResourceAsStream("openmoji-72x72-color.zip"),
                path -> new WithoutFileKey(java.nio.file.Files.readAttributes(path,
                        java.nio.file.attribute.BasicFileAttributes.class)))) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            try (var image = emoji.openImageStream("1f642")) {
                assertThat(emoji.validateArchive()).isTrue();

                assertThat(image.read()).isEqualTo(0x89);
                emoji.close();
                assertThatThrownBy(image::read).isInstanceOf(IOException.class);
            }
        }
    }

    @Test
    void anArchiveWithoutAFileKeyOwnsItsCopyWithoutPinningTheReplaceableSource() throws Exception {
        var opened = new java.util.ArrayList<Path>();
        try (var emoji = withoutFileKey(path -> {
            opened.add(path);
            return new java.util.zip.ZipFile(path.toFile());
        })) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            var firstCopy = opened.getFirst();
            assertThat(firstCopy).isNotEqualTo(emoji.getArchivePath()).exists();
            try (var image = emoji.openImageStream("1f642")) {
                var retired = directory.resolve("retired.zip");
                java.nio.file.Files.move(emoji.getArchivePath(), retired);
                java.nio.file.Files.writeString(retired, "invalid retired archive");
                try (var bundled = Emoji.class.getResourceAsStream("openmoji-72x72-color.zip")) {
                    java.nio.file.Files.copy(bundled, emoji.getArchivePath());
                }

                assertThat(emoji.validateArchive()).isTrue();

                assertThat(opened).hasSize(2);
                assertThat(firstCopy).doesNotExist();
                assertThatThrownBy(image::read).isInstanceOf(IOException.class);
                try (var current = emoji.openImageStream("1f642")) {
                    assertThat(current.read()).isEqualTo(0x89);
                }
            }
        }
        assertThat(opened).allSatisfy(path -> assertThat(path).doesNotExist());
    }

    @Test
    void overwritingTheSourceCannotChangeAnAlreadyVerifiedCopy() throws Exception {
        var opened = new java.util.ArrayList<Path>();
        try (var emoji = withoutFileKey(path -> {
            opened.add(path);
            return new java.util.zip.ZipFile(path.toFile());
        })) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            try (var image = emoji.openImageStream("1f642")) {
                java.nio.file.Files.writeString(emoji.getArchivePath(), "invalid public archive");

                assertThat(image.read()).isEqualTo(0x89);
                assertThat(emoji.validateArchive()).isFalse();

                assertThat(emoji.isReady()).isFalse();
                assertThatThrownBy(image::read).isInstanceOf(IOException.class);
                assertThat(opened).allSatisfy(path -> assertThat(path).doesNotExist());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aCopyWithoutAFileKeyIsDeletedWhenOpeningOrIndexingFails(boolean indexFailure) throws Exception {
        var failure = new IllegalStateException("synthetic ZIP initialization failure");
        var opened = new java.util.concurrent.atomic.AtomicReference<Path>();
        try (var emoji = withoutFileKey(path -> {
            opened.set(path);
            if (!indexFailure) throw failure;
            return new java.util.zip.ZipFile(path.toFile()) {
                @Override public java.util.stream.Stream<? extends java.util.zip.ZipEntry> stream() { throw failure; }
            };
        })) {
            emoji.extractArchive();

            assertThatThrownBy(emoji::validateArchive).isSameAs(failure);

            assertThat(emoji.isReady()).isFalse();
            assertThat(opened.get()).doesNotExist();
            assertThat(emoji.getArchivePath()).exists();
        }
    }

    @Test
    void aCopyWithoutAFileKeyIsDeletedEvenWhenZipCloseReportsFailure() throws Exception {
        var failure = new IOException("synthetic ZIP close failure");
        var opened = new java.util.concurrent.atomic.AtomicReference<Path>();
        try (var emoji = withoutFileKey(path -> {
            opened.set(path);
            return new java.util.zip.ZipFile(path.toFile()) {
                @Override public void close() throws IOException { super.close(); throw failure; }
            };
        })) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();

            assertThatThrownBy(emoji::close).isInstanceOf(java.io.UncheckedIOException.class).hasCause(failure);

            assertThat(emoji.isReady()).isFalse();
            assertThat(opened.get()).doesNotExist();
        }
    }

    private Emoji withoutFileKey(Emoji.ArchiveOpener opener) {
        return new Emoji(directory, java.nio.file.Files::newInputStream, opener,
                () -> Emoji.class.getResourceAsStream("openmoji-72x72-color.zip"),
                path -> new WithoutFileKey(java.nio.file.Files.readAttributes(path,
                        java.nio.file.attribute.BasicFileAttributes.class)));
    }

    private record WithoutFileKey(java.nio.file.attribute.BasicFileAttributes delegate)
            implements java.nio.file.attribute.BasicFileAttributes {
        @Override public java.nio.file.attribute.FileTime lastModifiedTime() { return delegate.lastModifiedTime(); }
        @Override public java.nio.file.attribute.FileTime lastAccessTime() { return delegate.lastAccessTime(); }
        @Override public java.nio.file.attribute.FileTime creationTime() { return delegate.creationTime(); }
        @Override public boolean isRegularFile() { return delegate.isRegularFile(); }
        @Override public boolean isDirectory() { return delegate.isDirectory(); }
        @Override public boolean isSymbolicLink() { return delegate.isSymbolicLink(); }
        @Override public boolean isOther() { return delegate.isOther(); }
        @Override public long size() { return delegate.size(); }
        @Override public Object fileKey() { return null; }
    }

    @Test
    void aZipReturnedAfterReentrantCloseIsReleasedWithoutBecomingReady() throws Exception {
        var owner = new java.util.concurrent.atomic.AtomicReference<Emoji>();
        var closes = new java.util.concurrent.atomic.AtomicInteger();
        try (var emoji = new Emoji(directory, java.nio.file.Files::newInputStream, path -> {
            owner.get().close();
            return new java.util.zip.ZipFile(path.toFile()) {
                @Override public void close() throws IOException { closes.incrementAndGet(); super.close(); }
            };
        })) {
            owner.set(emoji);
            emoji.extractArchive();

            assertThatThrownBy(emoji::validateArchive).isInstanceOf(IllegalStateException.class).hasMessage("closed");

            assertThat(emoji.isReady()).isFalse();
            assertThat(closes).hasValue(1);
            emoji.close();
            assertThat(closes).hasValue(1);
        }
    }

    @Test
    void replacementDuringHashReadCannotPublishUnverifiedFileContents() throws Exception {
        var swap = new java.util.concurrent.atomic.AtomicBoolean();
        try (var emoji = new Emoji(directory, path -> {
            var input = java.nio.file.Files.newInputStream(path);
            try {
                if (swap.getAndSet(false)) {
                    java.nio.file.Files.move(path, directory.resolve("retired.zip"));
                    java.nio.file.Files.writeString(path, "unverified replacement");
                }
                return input;
            } catch (IOException | RuntimeException failure) {
                input.close();
                throw failure;
            }
        }, path -> new java.util.zip.ZipFile(path.toFile()))) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            try (var image = emoji.openImageStream("1f642")) {
                swap.set(true);

                assertThatThrownBy(emoji::validateArchive).isInstanceOf(IOException.class)
                        .hasMessage("emoji archive changed during validation");

                assertThat(emoji.isReady()).isFalse();
                assertThatThrownBy(image::read).isInstanceOf(IOException.class);
                assertThat(java.nio.file.Files.readString(emoji.getArchivePath())).isEqualTo("unverified replacement");
            }
        }
    }

    @Test
    void indexFailureReleasesTheNewZipBeforeItCanBePublished() throws Exception {
        var failure = new IllegalStateException("cannot read ZIP entries");
        var closes = new java.util.concurrent.atomic.AtomicInteger();
        try (var emoji = new Emoji(directory, java.nio.file.Files::newInputStream,
                path -> new java.util.zip.ZipFile(path.toFile()) {
                    @Override public java.util.stream.Stream<? extends java.util.zip.ZipEntry> stream() { throw failure; }
                    @Override public void close() throws IOException { closes.incrementAndGet(); super.close(); }
                })) {
            emoji.extractArchive();

            assertThatThrownBy(emoji::validateArchive).isSameAs(failure);

            assertThat(emoji.isReady()).isFalse();
            assertThat(closes).hasValue(1);
            assertThatThrownBy(() -> emoji.openImageStream("1f642")).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void failedExtractionKeepsThePreviousArchiveAndCanBeRetried() throws Exception {
        var failure = new IOException("bundled archive read failed");
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var sourceClosed = new java.util.concurrent.atomic.AtomicBoolean();
        try (var emoji = new Emoji(directory, java.nio.file.Files::newInputStream,
                path -> new java.util.zip.ZipFile(path.toFile()), () -> {
                    if (!fail.get()) return Emoji.class.getResourceAsStream("openmoji-72x72-color.zip");
                    return new java.io.InputStream() {
                        int emitted;
                        @Override public int read() throws IOException {
                            if (emitted++ < 4) return 0;
                            throw failure;
                        }
                        @Override public void close() { sourceClosed.set(true); }
                    };
                })) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            var backup = directory.resolve("before.zip");
            java.nio.file.Files.copy(emoji.getArchivePath(), backup);
            try (var image = emoji.openImageStream("1f642")) {
                fail.set(true);

                assertThatThrownBy(emoji::extractArchive).isSameAs(failure);

                assertThat(sourceClosed).isTrue();
                assertThat(java.nio.file.Files.mismatch(backup, emoji.getArchivePath())).isEqualTo(-1);
                assertThat(emoji.isReady()).isTrue();
                assertThat(image.read()).isEqualTo(0x89);
                try (var files = java.nio.file.Files.list(directory)) {
                    assertThat(files.map(path -> path.getFileName().toString()))
                            .containsExactlyInAnyOrder("before.zip", "openmoji-72x72-color.zip");
                }
                fail.set(false);
                emoji.extractArchive();
                assertThat(emoji.isReady()).isFalse();
                assertThatThrownBy(image::read).isInstanceOf(IOException.class);
                assertThat(emoji.validateArchive()).isTrue();
                try (var recovered = emoji.openImageStream("1f642")) {
                    assertThat(recovered.read()).isEqualTo(0x89);
                }
            }
        }
    }

    @Test
    void checksumReadFailureKeepsBothCleanupFailuresAndReleasesTheArchive() throws Exception {
        var readFailure = new IOException("checksum read failed");
        var streamFailure = new IOException("checksum stream close failed");
        var zipFailure = new IOException("zip close failed");
        var fail = new java.util.concurrent.atomic.AtomicBoolean();
        var readsClosed = new java.util.concurrent.atomic.AtomicInteger();
        var zipsClosed = new java.util.concurrent.atomic.AtomicInteger();
        try (var emoji = new Emoji(directory, path -> new java.io.FilterInputStream(java.nio.file.Files.newInputStream(path)) {
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                if (fail.get()) throw readFailure;
                return super.read(bytes, offset, length);
            }
            @Override public void close() throws IOException {
                readsClosed.incrementAndGet();
                super.close();
                if (fail.get()) throw streamFailure;
            }
        }, path -> new java.util.zip.ZipFile(path.toFile()) {
            @Override public void close() throws IOException {
                zipsClosed.incrementAndGet();
                super.close();
                if (fail.get()) throw zipFailure;
            }
        })) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            try (var image = emoji.openImageStream("1f642")) {
                fail.set(true);

                assertThatThrownBy(emoji::validateArchive).isSameAs(readFailure).satisfies(failure -> {
                    assertThat(failure.getSuppressed()).hasSize(2);
                    assertThat(failure.getSuppressed()[0]).isSameAs(streamFailure);
                    assertThat(failure.getSuppressed()[1]).isInstanceOf(java.io.UncheckedIOException.class).hasCause(zipFailure);
                });

                assertThat(emoji.isReady()).isFalse();
                assertThat(readsClosed).hasValue(2);
                assertThat(zipsClosed).hasValue(1);
                assertThatThrownBy(image::read).isInstanceOf(IOException.class);
                fail.set(false);
                assertThat(emoji.validateArchive()).isTrue();
            }
        }
    }

    @Test
    void validationOpensTheReplacementFileInsteadOfAStaleHandleToTheOldFile() throws Exception {
        try (var emoji = new Emoji(directory)) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            try (var oldInput = emoji.openImageStream("1f642")) {
                var previous = directory.resolve("previous.zip");
                java.nio.file.Files.move(emoji.getArchivePath(), previous);
                java.nio.file.Files.writeString(previous, "old file is no longer a valid archive");
                try (var bundled = Emoji.class.getResourceAsStream("openmoji-72x72-color.zip")) {
                    java.nio.file.Files.copy(bundled, emoji.getArchivePath());
                }

                assertThat(emoji.validateArchive()).isTrue();

                try (var current = emoji.openImageStream("1f642")) {
                    assertThat(current.read()).isEqualTo(0x89);
                }
                assertThatThrownBy(oldInput::read).isInstanceOf(IOException.class);
            }
        }
    }

    @Test
    void aValidationReadFailureReleasesThePreviouslyOpenedArchive() throws Exception {
        var failure = new IOException("archive cannot be read");
        var failRead = new java.util.concurrent.atomic.AtomicBoolean();
        try (var emoji = new Emoji(directory, path -> {
            if (failRead.get()) throw failure;
            return java.nio.file.Files.newInputStream(path);
        }, path -> new java.util.zip.ZipFile(path.toFile()))) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            try (var input = emoji.openImageStream("1f642")) {
                failRead.set(true);

                assertThatThrownBy(emoji::validateArchive).isSameAs(failure);

                assertThat(emoji.isReady()).isFalse();
                assertThatThrownBy(input::read).isInstanceOf(IOException.class);
                failRead.set(false);
                assertThat(emoji.validateArchive()).isTrue();
            }
        }
    }

    @Test
    void archiveCloseFailureIsReportedAfterItsStateIsReleased() throws Exception {
        var failure = new IOException("archive close failed");
        var closes = new java.util.concurrent.atomic.AtomicInteger();
        try (var emoji = new Emoji(directory, java.nio.file.Files::newInputStream,
                path -> new java.util.zip.ZipFile(path.toFile()) {
                    @Override public void close() throws IOException {
                        closes.incrementAndGet();
                        super.close();
                        throw failure;
                    }
                })) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();

            assertThatThrownBy(emoji::close).isInstanceOf(java.io.UncheckedIOException.class).hasCause(failure);

            assertThat(emoji.isReady()).isFalse();
            emoji.close();
            assertThat(closes).hasValue(1);
        }
    }

    @Test
    void failedRevalidationReleasesTheOldArchiveAndAllowsAValidatedReplacement() throws Exception {
        try (var emoji = new Emoji(directory)) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            try (var input = emoji.openImageStream("1f642")) {
                var replacement = directory.resolve("invalid.zip");
                java.nio.file.Files.writeString(replacement, "invalid archive");
                java.nio.file.Files.move(replacement, emoji.getArchivePath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                assertThat(emoji.validateArchive()).isFalse();

                assertThat(emoji.isReady()).isFalse();
                assertThatThrownBy(() -> emoji.contains("1f642")).isInstanceOf(IllegalStateException.class);
                assertThatThrownBy(input::read).isInstanceOf(IOException.class);
                emoji.extractArchive();
                assertThat(emoji.validateArchive()).isTrue();
                try (var recovered = emoji.openImageStream("1f642")) {
                    assertThat(recovered.read()).isEqualTo(0x89);
                }
            }
        }
    }

    @Test
    void closeWaitsForValidationBeforeReleasingTheArchive() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closeStarted = new CountDownLatch(1);
        var emoji = new Emoji(directory, path -> {
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("validation was not released");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            return java.nio.file.Files.newInputStream(path);
        }, path -> new java.util.zip.ZipFile(path.toFile()));
        emoji.extractArchive();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            var validation = worker.submit(emoji::validateArchive);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                var closing = worker.submit(() -> {
                    closeStarted.countDown();
                    emoji.close();
                });
                try {
                    assertThat(closeStarted.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> closing.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                } finally {
                    release.countDown();
                    closing.get(5, TimeUnit.SECONDS);
                }
                assertThat(validation.get(5, TimeUnit.SECONDS)).isTrue();
                assertThat(emoji.isReady()).isFalse();
            } finally {
                release.countDown();
                validation.get(5, TimeUnit.SECONDS);
            }
        } finally {
            release.countDown();
            emoji.close();
        }
    }

    @Test
    void closingAnOpenedArchiveClearsItsReadiness() throws Exception {
        try (var emoji = new Emoji(directory)) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            assertThat(emoji.isReady()).isTrue();

            emoji.close();

            assertThat(emoji.isReady()).isFalse();
            assertThatThrownBy(() -> emoji.contains("1f642"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("closed");
            assertThatThrownBy(() -> emoji.openImageStream("1f642"))
                    .isInstanceOf(IllegalStateException.class).hasMessage("closed");
        }
    }

    @Test
    void repeatedValidationKeepsTheOpenedStreamOwnedUntilClose() throws Exception {
        try (var emoji = new Emoji(directory)) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            try (var input = emoji.openImageStream("1f642")) {
                assertThat(emoji.validateArchive()).isTrue();
                assertThat(input.read()).isEqualTo(0x89);

                emoji.close();

                assertThatThrownBy(input::read).isInstanceOf(IOException.class);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void closedArchiveCannotBeOpenedAgain(boolean extract) {
        var emoji = new Emoji(directory);
        emoji.close();

        assertThatThrownBy(() -> {
            if (extract) emoji.extractArchive();
            else emoji.validateArchive();
        })
                .isInstanceOf(IllegalStateException.class).hasMessage("closed");
        assertThat(emoji.getArchivePath()).doesNotExist();
    }
}
