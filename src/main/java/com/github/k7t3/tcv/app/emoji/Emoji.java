/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.app.emoji;

import com.github.k7t3.tcv.lifecycle.CloseActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 絵文字アーカイブを所有し、画像の入力 stream を提供する。
 * 検証・配置と終了は直列化する。呼び出し側は返された stream を閉じる。
 */
public class Emoji implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Emoji.class);
    private static final String FILE_NAME = "openmoji-72x72-color.zip";

    private final Object archiveLock = new Object();
    @FunctionalInterface interface ArchiveInput { InputStream open(Path path) throws IOException; }
    @FunctionalInterface interface ArchiveOpener { ZipFile open(Path path) throws IOException; }
    @FunctionalInterface interface ArchiveResource { InputStream open() throws IOException; }
    @FunctionalInterface interface ArchiveMetadata { BasicFileAttributes read(Path path) throws IOException; }

    private final Path archivePath;
    private final ArchiveInput input;
    private final ArchiveOpener opener;
    private final ArchiveResource resource;
    private final ArchiveMetadata metadata;
    private EmojiProperties properties;
    private OpenArchive archive;

    private record OpenArchive(ZipFile zip, Map<String, String> entries, FileVersion version, Path copy) {}

    private record FileVersion(Object key, long size, FileTime modified, FileTime created) {
        static FileVersion read(BasicFileAttributes attributes) {
            return new FileVersion(attributes.fileKey(), attributes.size(),
                    attributes.lastModifiedTime(), attributes.creationTime());
        }

    }

    /** 識別子がない保存先では、ハッシュを検証した同じbytesをZIPの所有期間中保持する。 */
    private static final class ArchiveCopy implements AutoCloseable {
        private final Path path;
        private boolean adopted;

        private ArchiveCopy(FileVersion version) throws IOException {
            path = version.key() == null ? Files.createTempFile("tcv-emoji-verified-", ".zip") : null;
        }

        private String hash(InputStream input) throws IOException {
            if (path == null) return computeHash(input);
            try (var output = Files.newOutputStream(path)) { return computeHash(input, output); }
        }

        private Path adopt() { adopted = true; return path; }

        @Override public void close() throws IOException {
            if (!adopted && path != null) Files.deleteIfExists(path);
        }
    }

    private boolean closed = false;

    public Emoji(Path extractDir) {
        this(extractDir, Files::newInputStream, path -> new ZipFile(path.toFile()));
    }

    Emoji(Path extractDir, ArchiveInput input, ArchiveOpener opener) {
        this(extractDir, input, opener, Emoji::bundledArchive);
    }

    Emoji(Path extractDir, ArchiveInput input, ArchiveOpener opener, ArchiveResource resource) {
        this(extractDir, input, opener, resource, path -> Files.readAttributes(path, BasicFileAttributes.class));
    }

    Emoji(Path extractDir, ArchiveInput input, ArchiveOpener opener, ArchiveResource resource, ArchiveMetadata metadata) {
        this.archivePath = Objects.requireNonNull(extractDir).resolve(FILE_NAME);
        this.input = Objects.requireNonNull(input);
        this.opener = Objects.requireNonNull(opener);
        this.resource = Objects.requireNonNull(resource);
        this.metadata = Objects.requireNonNull(metadata);
    }

    private FileVersion readVersion(Path path) throws IOException { return FileVersion.read(metadata.read(path)); }

    private static InputStream bundledArchive() throws IOException {
        var input = Emoji.class.getResourceAsStream(FILE_NAME);
        if (input == null) throw new IOException("missing bundled emoji archive");
        return input;
    }

    public boolean contains(String hex) {
        synchronized (archiveLock) {
            checkReady();
            return archive.entries().containsKey(hex);
        }
    }

    /**
     * キーにマッチする絵文字のイメージをロードする{@link InputStream}を返す。
     * @param hex 絵文字のHexキー
     * @return 呼び出し側が閉じる入力 stream。アーカイブの置換・終了でも無効になる
     * @throws IllegalArgumentException キーに対応する画像がない場合
     * @throws IllegalStateException 検証前または終了済みの場合
     * @throws IOException 画像の入力 stream を開けない場合
     */
    public InputStream openImageStream(String hex) throws IOException {
        synchronized (archiveLock) {
            checkReady();
            var key = archive.entries().get(hex);
            if (key == null) throw new IllegalArgumentException();

            var zip = archive.zip();
            var entry = zip.getEntry(key);
            return zip.getInputStream(entry);
        }
    }

    /**
     * 配置先の親ディレクトリを作成し、同梱アーカイブを抽出する。
     * @throws IllegalStateException 終了している場合
     * @throws IOException アーカイブの配置に失敗した場合
     */
    public void extractArchive() throws IOException {
        synchronized (archiveLock) {
            checkOpen();
            var target = archivePath.toAbsolutePath();
            Files.createDirectories(target.getParent());
            var temporary = Files.createTempFile(target.getParent(), ".tcv-emoji-", ".tmp");
            Throwable primary = null;
            try {
                try (var input = resource.open()) {
                    Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                try (var input = Files.newInputStream(temporary)) {
                    if (!properties().getHash().equals(computeHash(input)))
                        throw new IOException("invalid bundled emoji archive");
                }
                releaseArchive();
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException | Error failure) {
                primary = failure;
                throw failure;
            } finally {
                try { Files.deleteIfExists(temporary); }
                catch (IOException cleanup) {
                    if (primary == null) throw cleanup;
                    if (primary != cleanup) primary.addSuppressed(cleanup);
                }
            }
        }
    }

    /**
     * 絵文字アーカイブファイルが所定のパスに抽出されているかを判定する。
     *
     * @return 抽出されているファイルが適切なものである場合はtrueを返す。
     * @throws IllegalStateException 終了している場合
     * @throws IOException アーカイブを開けない場合
     */
    public boolean validateArchive() throws IOException {
        synchronized (archiveLock) {
            checkOpen();
            try {
                var properties = properties();

                var path = getArchivePath();
                if (Files.exists(path)) {
                    var version = readVersion(path);
                    try (var copy = new ArchiveCopy(version)) {
                        final String hash;
                        try (var input = this.input.open(path)) {
                            hash = copy.hash(input);
                        }
                        checkOpen();
                        if (properties.getHash().equals(hash)) {
                            if (!version.equals(readVersion(path)))
                                throw new IOException("emoji archive changed during validation");
                            // 識別子がない場合も、所有コピーは検証済み内容から変更されない。
                            if (archive != null && version.equals(archive.version())) return true;
                            releaseArchive();
                            checkOpen();
                            var zip = opener.open(copy.path == null ? path : copy.path);
                            try {
                                checkOpen();
                                if (!version.equals(readVersion(path)))
                                    throw new IOException("emoji archive changed while opening");
                                var entries = new HashMap<String, String>();
                                zip.stream().map(ZipEntry::getName).forEach(name -> {
                                    var hex = name.toLowerCase(Locale.ROOT);
                                    var index = hex.indexOf(".");
                                    if (0 < index) {
                                        hex = hex.substring(0, index);
                                    }
                                    entries.put(hex, name);
                                });
                                checkOpen();
                                archive = new OpenArchive(zip, Collections.unmodifiableMap(entries), version, copy.adopt());
                                return true;
                            } catch (IOException | RuntimeException | Error failure) {
                                try { closeZip(zip); }
                                catch (RuntimeException | Error cleanup) {
                                    if (cleanup != failure) failure.addSuppressed(cleanup);
                                }
                                throw failure;
                            }
                        }
                    }
                }
                releaseArchive();
                return false;
            } catch (IOException | RuntimeException | Error failure) {
                try { releaseArchive(); }
                catch (RuntimeException | Error cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }
    }

    private EmojiProperties properties() {
        if (properties == null) {
            var loaded = new EmojiProperties();
            loaded.loadEmojiProperties();
            LOGGER.info("emoji properties {}", loaded);
            properties = loaded;
        }
        return properties;
    }

    private void checkReady() {
        checkOpen();
        if (archive == null) throw new IllegalStateException("not ready");
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("closed");
    }

    /** 進行中の検証・配置を待ち、アーカイブを一度だけ閉じる。返却済みの stream も無効になる。 */
    @Override
    public void close() {
        synchronized (archiveLock) {
            if (closed) return;
            closed = true;
            releaseArchive();
        }
    }

    private void releaseArchive() {
        var previous = archive;
        archive = null;
        if (previous != null) CloseActions.runAll(() -> closeZip(previous.zip()), () -> {
            if (previous.copy() != null) {
                try { Files.deleteIfExists(previous.copy()); }
                catch (IOException failure) { throw new UncheckedIOException("failed to delete owned emoji archive " + previous.copy(), failure); }
            }
        });
    }

    private static void closeZip(ZipFile zip) {
        try { zip.close(); }
        catch (IOException failure) { throw new UncheckedIOException("failed to close emoji archive", failure); }
    }

    private static String computeHash(InputStream input) throws IOException {
        return computeHash(input, OutputStream.nullOutputStream());
    }

    private static String computeHash(InputStream input, OutputStream output) throws IOException {
        final MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
        var bytes = new byte[8192];
        int length;
        while ((length = input.read(bytes)) != -1) {
            digest.update(bytes, 0, length);
            output.write(bytes, 0, length);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    public EmojiProperties getProperties() {
        synchronized (archiveLock) {
            return properties;
        }
    }

    public final Path getArchivePath() { return archivePath; }

    public boolean isReady() {
        synchronized (archiveLock) {
            return archive != null;
        }
    }
}
