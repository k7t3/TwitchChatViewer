package com.github.k7t3.tcv.build;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinuxGlibcRequirementTest {
    @TempDir Path directory;

    @Test
    void theRequiredVersionComesFromTheHighestNativeReferenceAcrossModuleJars() throws Exception {
        module("base.jar", elf("GLIBC_2.17", "GLIBC_2.9"));
        module("graphics.jar", elf("GLIBC_2.38"));

        var required = LinuxGlibcRequirement.minimumVersion(directory);

        assertThat(required).contains("2.38");
    }

    @Test
    void optionalWeakVersionsDoNotRaiseTheRequiredRuntimeVersion() throws Exception {
        var data = ByteBuffer.wrap(elf("GLIBC_2.17", "GLIBC_9.99")).order(ByteOrder.LITTLE_ENDIAN);
        var needs = Math.toIntExact(data.getLong(216));
        data.putShort(needs + 16 + 16 + 4, (short) 2);
        module("graphics.jar", data.array());

        assertThat(LinuxGlibcRequirement.minimumVersion(directory)).contains("2.17");
    }

    @Test
    void unknownGlibcRequirementsCannotSilentlyDeclareAnOlderRuntime() throws Exception {
        module("graphics.jar", elf("GLIBC_2.17", "GLIBC_FUTURE"));

        assertThatThrownBy(() -> LinuxGlibcRequirement.minimumVersion(directory))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("graphics.jar!/libsample.so")
                .hasRootCauseMessage("Unsupported GLIBC version requirement: GLIBC_FUTURE");
    }

    @Test
    void unreferencedVersionTextDoesNotRaiseTheRuntimeRequirement() throws Exception {
        var library = elf("GLIBC_2.17");
        var extra = "GLIBC_99.0\0".getBytes(StandardCharsets.US_ASCII);
        var withDebugText = Arrays.copyOf(library, library.length + extra.length);
        System.arraycopy(extra, 0, withDebugText, library.length, extra.length);
        module("graphics.jar", withDebugText);

        assertThat(LinuxGlibcRequirement.minimumVersion(directory)).contains("2.17");
    }

    @Test
    void modulesWithoutNativeLibrariesHaveNoAdditionalGlibcRequirement() throws Exception {
        try (var archive = new ZipOutputStream(Files.newOutputStream(directory.resolve("controls.jar")))) {
            archive.putNextEntry(new ZipEntry("fixture/Control.class"));
            archive.write("GLIBC_99.0".getBytes(StandardCharsets.US_ASCII));
            archive.closeEntry();
        }

        assertThat(LinuxGlibcRequirement.minimumVersion(directory)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedMetadata")
    void malformedMetadataFailsWithTheArchiveAndResourceContext(String reason, byte[] bytes) throws Exception {
        module("graphics.jar", bytes);

        assertThatThrownBy(() -> LinuxGlibcRequirement.minimumVersion(directory))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("graphics.jar!/libsample.so");
    }

    @Test
    void theInstallerDeclaresTheAbiRequiredByItsResolvedJavafxModules() throws Exception {
        module("graphics.jar", elf("GLIBC_2.17", "GLIBC_2.38"));
        var input = Files.createDirectories(directory.resolve("input"));
        Files.writeString(input.resolve("app.jar"), "synthetic application fixture");
        Files.writeString(input.resolve("key-storage.properties"), "main-class=fixture.Helper");
        Files.createDirectories(directory.resolve("docs"));
        Files.writeString(directory.resolve("docs/license.md"), "synthetic license");
        var icons = Files.createDirectories(directory.resolve("src/main/resources/icons"));
        Files.writeString(icons.resolve("TwitchChatViewer-64.png"), "synthetic icon");
        var plan = new PackagePlan.Inputs(PackagePlan.Platform.LINUX, false, "1.0.0", input, directory,
                "app.jar", "fixture.Main", directory, List.of("java.base"));

        var arguments = PackagePlan.arguments(plan, directory.resolve("output"));

        assertThat(arguments).contains("--linux-package-deps");
        assertThat(arguments.get(arguments.indexOf("--linux-package-deps") + 1))
                .contains("libc6 (>= 2.38)");
    }

    private static Stream<Arguments> malformedMetadata() throws Exception {
        var valid = elf("GLIBC_2.17", "GLIBC_2.38");
        var header = ByteBuffer.wrap(valid).order(ByteOrder.LITTLE_ENDIAN);
        var needs = Math.toIntExact(header.getLong(216));
        var lastStringByte = Math.toIntExact(header.getLong(152) + header.getLong(160) - 1);
        return Stream.of(
                Arguments.of("truncated header", Arrays.copyOf(valid, 32)),
                Arguments.of("unsupported byte order", changed(valid, data -> data.put(5, (byte) 2))),
                Arguments.of("section-table overflow", changed(valid, data -> data.putLong(40, Long.MAX_VALUE))),
                Arguments.of("invalid string-table link", changed(valid, data -> data.putInt(232, 99))),
                Arguments.of("invalid version string index", changed(valid, data -> data.putInt(needs + 16 + 8, Integer.MAX_VALUE))),
                Arguments.of("unterminated version string", changed(valid, data -> data.put(lastStringByte, (byte) 'x'))),
                Arguments.of("truncated auxiliary chain", changed(valid, data -> data.putInt(needs + 16 + 12, 0))),
                Arguments.of("unsupported numeric version", elf("GLIBC_2.99999999999999999999")));
    }

    private static byte[] changed(byte[] bytes, Consumer<ByteBuffer> edit) {
        var result = bytes.clone();
        edit.accept(ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN));
        return result;
    }

    private void module(String name, byte[] nativeLibrary) throws Exception {
        try (var archive = new ZipOutputStream(Files.newOutputStream(directory.resolve(name)))) {
            archive.putNextEntry(new ZipEntry("libsample.so"));
            archive.write(nativeLibrary);
            archive.closeEntry();
        }
    }

    /** ELF64の公開ABIどおりに、文字列表と1件のVerneed/Vernaux列を持つfixtureを作る。 */
    private static byte[] elf(String... versions) throws Exception {
        var strings = new ByteArrayOutputStream();
        strings.write(0);
        strings.write("libc.so.6\0".getBytes(StandardCharsets.US_ASCII));
        var names = new ArrayList<Integer>();
        for (var version : versions) {
            names.add(strings.size());
            strings.write((version + "\0").getBytes(StandardCharsets.US_ASCII));
        }
        var stringOffset = 256;
        var needsOffset = (stringOffset + strings.size() + 7) / 8 * 8;
        var needsSize = 16 + 16 * versions.length;
        var data = ByteBuffer.allocate(needsOffset + needsSize).order(ByteOrder.LITTLE_ENDIAN);
        data.put(new byte[]{0x7f, 'E', 'L', 'F', 2, 1, 1});
        data.putShort(16, (short) 3).putShort(18, (short) 183).putInt(20, 1);
        data.putLong(40, 64).putShort(52, (short) 64).putShort(58, (short) 64).putShort(60, (short) 3);
        data.putInt(132, 3).putLong(152, stringOffset).putLong(160, strings.size());
        data.putInt(196, 0x6ffffffe).putLong(216, needsOffset).putLong(224, needsSize).putInt(232, 1).putInt(236, 1);
        data.position(stringOffset);
        data.put(strings.toByteArray());
        data.putShort(needsOffset, (short) 1).putShort(needsOffset + 2, (short) versions.length)
                .putInt(needsOffset + 4, 1).putInt(needsOffset + 8, 16);
        for (var i = 0; i < versions.length; i++) {
            var auxiliary = needsOffset + 16 + i * 16;
            data.putInt(auxiliary + 8, names.get(i)).putInt(auxiliary + 12, i + 1 == versions.length ? 0 : 16);
        }
        return data.array();
    }
}
