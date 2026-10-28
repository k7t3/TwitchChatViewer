package com.github.k7t3.tcv.build;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** 解決済みの対象OS/CPU向けJavaFX JARから、ELF64の強いGLIBCバージョン参照を読む。 */
final class LinuxGlibcRequirement {
    private static final int VERSION_NEEDS = 0x6ffffffe;
    private static final int WEAK_VERSION = 2;
    private static final Pattern GLIBC_VERSION = Pattern.compile("GLIBC_(\\d+(?:\\.\\d+)+)");
    private LinuxGlibcRequirement() {}

    /** 解析不能なネイティブ資源は、要件を省略せずIOExceptionで報告する。 */
    static Optional<String> minimumVersion(Path moduleDirectory) throws IOException {
        Version highest = null;
        try (var files = Files.list(moduleDirectory)) {
            for (var path : files.filter(file -> file.getFileName().toString().endsWith(".jar")).sorted().toList()) {
                try (var archive = new ZipFile(path.toFile())) {
                    var entries = archive.entries();
                    while (entries.hasMoreElements()) {
                        var entry = entries.nextElement();
                        if (entry.isDirectory() || !entry.getName().endsWith(".so")) continue;
                        try (var input = archive.getInputStream(entry)) {
                            highest = maximum(highest, read(input.readAllBytes()));
                        } catch (IOException failure) {
                            throw new IOException("Cannot inspect native ABI: " + path + "!/" + entry.getName(), failure);
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(highest).map(Version::text);
    }

    private static Version read(byte[] bytes) throws IOException {
        try {
            if (bytes.length < 64 || bytes[0] != 0x7f || bytes[1] != 'E' || bytes[2] != 'L'
                    || bytes[3] != 'F' || bytes[4] != 2 || bytes[5] != 1)
                throw new IOException("Expected a little-endian ELF64 native resource");
            var data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            var table = Math.toIntExact(data.getLong(40));
            var entrySize = Short.toUnsignedInt(data.getShort(58));
            var count = Short.toUnsignedInt(data.getShort(60));
            if (entrySize < 64 || count == 0) throw new IOException("ELF section headers unavailable");
            range(table, Math.multiplyExact(entrySize, count), bytes.length);
            Version highest = null;
            // 未使用の文字列や定義を拾わず、SHT_GNU_verneedとそのVernauxだけを辿る。
            for (var i = 0; i < count; i++) {
                var header = table + i * entrySize;
                if (data.getInt(header + 4) != VERSION_NEEDS) continue;
                var linked = data.getInt(header + 40);
                if (linked < 0 || linked >= count) throw new IOException("Invalid ELF version string-table link");
                var stringHeader = table + linked * entrySize;
                if (data.getInt(stringHeader + 4) != 3) throw new IOException("ELF version link is not a string table");
                var strings = section(data, stringHeader);
                var needs = section(data, header);
                var record = needs.offset();
                while (record < needs.end()) {
                    range(record, 16, needs.end());
                    if (data.getShort(record) != 1) throw new IOException("Unsupported ELF version-needs revision");
                    var versions = Short.toUnsignedInt(data.getShort(record + 2));
                    var auxiliary = Math.addExact(record, unsigned(data, record + 8));
                    for (var j = 0; j < versions; j++) {
                        range(auxiliary, 16, needs.end());
                        var name = string(bytes, strings, unsigned(data, auxiliary + 8));
                        var match = GLIBC_VERSION.matcher(name);
                        if ((data.getShort(auxiliary + 4) & WEAK_VERSION) == 0) {
                            if (match.matches()) highest = maximum(highest, new Version(match.group(1)));
                            else if (name.startsWith("GLIBC_")) throw new IOException("Unsupported GLIBC version requirement: " + name);
                        }
                        var next = unsigned(data, auxiliary + 12);
                        if (j + 1 < versions && next < 16) throw new IOException("Truncated ELF auxiliary version chain");
                        auxiliary = Math.addExact(auxiliary, next);
                    }
                    var next = unsigned(data, record + 12);
                    if (next == 0) break;
                    if (next < 16) throw new IOException("Invalid ELF version-needs chain");
                    record = Math.addExact(record, next);
                    range(record, 16, needs.end());
                }
            }
            return highest;
        } catch (IndexOutOfBoundsException | ArithmeticException | NumberFormatException failure) {
            throw new IOException("Invalid ELF native version metadata", failure);
        }
    }

    private static Section section(ByteBuffer data, int header) throws IOException {
        var offset = Math.toIntExact(data.getLong(header + 24));
        var size = Math.toIntExact(data.getLong(header + 32));
        range(offset, size, data.limit());
        return new Section(offset, size);
    }

    private static String string(byte[] bytes, Section table, int index) throws IOException {
        if (index < 0 || index >= table.size()) throw new IOException("Invalid ELF version string index");
        var start = table.offset() + index;
        var end = start;
        while (end < table.end() && bytes[end] != 0) end++;
        if (end == table.end()) throw new IOException("Unterminated ELF version string");
        return new String(bytes, start, end - start, StandardCharsets.US_ASCII);
    }

    private static int unsigned(ByteBuffer data, int offset) { return Math.toIntExact(Integer.toUnsignedLong(data.getInt(offset))); }

    private static void range(int offset, int size, int limit) throws IOException {
        if (offset < 0 || size < 0 || offset > limit || size > limit - offset) throw new IOException("ELF metadata range outside section");
    }

    private static Version maximum(Version left, Version right) {
        return right == null || left != null && left.compareTo(right) >= 0 ? left : right;
    }

    private record Section(int offset, int size) { int end() { return offset + size; } }

    private record Version(String text, List<Integer> parts) implements Comparable<Version> {
        Version(String text) { this(text, Arrays.stream(text.split("\\.")).map(Integer::parseInt).toList()); }
        @Override public int compareTo(Version other) {
            for (var i = 0; i < Math.max(parts.size(), other.parts.size()); i++) {
                var comparison = Integer.compare(i < parts.size() ? parts.get(i) : 0, i < other.parts.size() ? other.parts.get(i) : 0);
                if (comparison != 0) return comparison;
            }
            return 0;
        }
    }
}
