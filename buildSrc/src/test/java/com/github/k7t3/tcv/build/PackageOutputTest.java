package com.github.k7t3.tcv.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class PackageOutputTest {
    @TempDir Path directory;

    @Test
    void aRetirementFailureReportsThePublishedPackageAndItsOwnedRemnant() throws Exception {
        var output = previous();
        Files.writeString(output.resolve("retained.dmg"), "remaining old package");
        var failure = new DirectoryNotEmptyException("synthetic retired directory");
        var retired = new AtomicReference<Path>();

        var result = PackageOutput.produce(output, staging -> Files.writeString(staging.resolve("new.dmg"), "new"),
                (source, target) -> Files.move(source, target, StandardCopyOption.ATOMIC_MOVE), path -> {
                    if (path.getFileName().toString().startsWith(".tcv-previous-")) {
                        retired.set(path);
                        Files.delete(path.resolve("previous.dmg"));
                        throw failure;
                    }
                    Files.deleteIfExists(path);
                });

        assertThat(result.output()).isEqualTo(output.toAbsolutePath().normalize());
        assertThat(Files.readString(output.resolve("new.dmg"))).isEqualTo("new");
        assertThat(result.cleanupFailures()).singleElement().satisfies(cleanup -> {
            assertThat(cleanup.directory()).isEqualTo(retired.get());
            assertThat(cleanup.failure()).isSameAs(failure);
        });
        assertThat(Files.readString(retired.get().resolve("retained.dmg"))).isEqualTo("remaining old package");
        assertThat(retired.get().resolve("previous.dmg")).doesNotExist();
    }

    @Test
    void aSuccessfulPackageReplacesThePreviousDirectoryAfterGeneration() throws Exception {
        var output = previous();
        PackageOutput.produce(output, stage -> {
            assertThat(Files.readString(output.resolve("previous.dmg"))).isEqualTo("previous");
            Files.writeString(stage.resolve("new.dmg"), "new");
        });
        assertThat(Files.readString(output.resolve("new.dmg"))).isEqualTo("new"); assertThat(output.resolve("previous.dmg")).doesNotExist();
        assertNoTemporaryDirectories();
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void aGeneratorFailureKeepsTheOldPackageAndRemovesOnlyItsOwnStaging(boolean error) throws Exception {
        var output = previous(); Throwable failure = error ? new AssertionError("generator failed") : new IOException("generator failed");
        assertThatThrownBy(() -> PackageOutput.produce(output, stage -> {
            Files.writeString(stage.resolve("partial.dmg"), "partial");
            if (failure instanceof Error fatal) throw fatal; throw (Exception) failure;
        })).isSameAs(failure);
        assertThat(Files.readString(output.resolve("previous.dmg"))).isEqualTo("previous"); assertNoTemporaryDirectories();
    }

    @Test
    void publicationFailureRestoresThePreviousDirectoryAndPreservesTheCause() throws Exception {
        var output = previous(); var failure = new IOException("publication failed");
        assertThatThrownBy(() -> PackageOutput.produce(output, stage -> Files.writeString(stage.resolve("new.dmg"), "new"), (source, target) -> {
            if (source.getFileName().toString().startsWith(".tcv-package-")) throw failure;
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        })).isSameAs(failure);
        assertThat(Files.readString(output.resolve("previous.dmg"))).isEqualTo("previous"); assertNoTemporaryDirectories();
    }

    @Test
    void failedRollbackKeepsTheBackupAndReportsItsLocationAlongsideTheOriginalFailures() throws Exception {
        var output = previous(); var original = new IOException("publication failed"); var rollback = new IOException("restore failed");
        assertThatThrownBy(() -> PackageOutput.produce(output, stage -> Files.writeString(stage.resolve("new.dmg"), "new"), (source, target) -> {
            if (source.getFileName().toString().startsWith(".tcv-package-")) throw original;
            if (source.getFileName().toString().startsWith(".tcv-previous-")) throw rollback;
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        })).isSameAs(original).satisfies(failure -> {
            assertThat(failure.getSuppressed()).contains(rollback);
            assertThat(failure.getSuppressed()).anySatisfy(suppressed -> assertThat(suppressed.getMessage()).contains("Previous package remains at"));
        });
        try (var children = Files.list(directory)) {
            var backup = children.filter(path -> path.getFileName().toString().startsWith(".tcv-previous-")).findFirst().orElseThrow();
            assertThat(Files.readString(backup.resolve("previous.dmg"))).isEqualTo("previous");
        }
    }

    @Test
    void aFirstFailedPackageDoesNotCreateAnEmptySuccessfulOutput() {
        var output = directory.resolve("app"); var failure = new IOException("first package failed");
        assertThatThrownBy(() -> PackageOutput.produce(output, stage -> { throw failure; })).isSameAs(failure);
        assertThat(output).doesNotExist(); assertNoTemporaryDirectories();
    }

    @Test
    void anEmptyGenerationDoesNotReplaceThePreviousPackage() throws Exception {
        var output = previous();
        assertThatThrownBy(() -> PackageOutput.produce(output, stage -> {}))
                .isInstanceOf(IOException.class).hasMessageContaining("no package");
        assertThat(Files.readString(output.resolve("previous.dmg"))).isEqualTo("previous");
        assertNoTemporaryDirectories();
    }

    @Test
    void anExistingNonDirectoryIsNeverReplaced() throws Exception {
        var output = directory.resolve("app"); Files.writeString(output, "unrelated file");
        assertThatThrownBy(() -> PackageOutput.produce(output, stage -> Files.writeString(stage.resolve("new.dmg"), "new")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Files.readString(output)).isEqualTo("unrelated file"); assertNoTemporaryDirectories();
    }

    private Path previous() throws Exception {
        var output = directory.resolve("app"); Files.createDirectories(output); Files.writeString(output.resolve("previous.dmg"), "previous"); return output;
    }
    private void assertNoTemporaryDirectories() {
        try (var children = Files.list(directory)) {
            assertThat(children.filter(path -> path.getFileName().toString().startsWith(".tcv-")).toList()).isEmpty();
        } catch (IOException failure) { throw new AssertionError(failure); }
    }
}
