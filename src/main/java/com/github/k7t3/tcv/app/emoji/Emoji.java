package com.github.k7t3.tcv.app.emoji;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 絵文字イメージをロードするためのクラス。
 */
public class Emoji implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(Emoji.class);
    private static final String FILE_NAME = "openmoji-72x72-color.zip";

    private final Map<String, String> hexAndName = new HashMap<>();
    private final ObjectProperty<Path> archivePath = new SimpleObjectProperty<>();
    private final ReadOnlyBooleanWrapper ready = new ReadOnlyBooleanWrapper(false);
    private final AtomicReference<EmojiProperties> properties = new AtomicReference<>();
    private final AtomicReference<ZipFile> zipFile = new AtomicReference<>();

    private boolean closed = false;

    public Emoji(Path extractDir) {
        setArchivePath(extractDir.resolve(FILE_NAME));
    }

    public boolean contains(String hex) {
        checkReady();
        return hexAndName.containsKey(hex);
    }

    /**
     * キーにマッチする絵文字のイメージをロードする{@link InputStream}を返す。
     * @param hex 絵文字のHexキー
     * @return 絵文字のイメージInputStream
     * @throws IOException キーにマッチするイメージがないときにスローされる
     */
    public InputStream openImageStream(String hex) throws IOException {
        checkReady();
        var key = hexAndName.get(hex);
        if (key == null) throw new IllegalArgumentException();

        var zip = zipFile.get();
        var entry = zip.getEntry(key);
        return zip.getInputStream(entry);
    }

    /**
     * アーカイブを抽出する
     */
    public void extractArchive() throws IOException {
        final var archivePath = getArchivePath();
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream(FILE_NAME))) {
            Files.copy(input, archivePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * 絵文字アーカイブファイルが所定のパスに抽出されているかを判定する。
     *
     * @return 抽出されているファイルが適切なものである場合はtrueを返す。
     */
    public boolean validateArchive() throws IOException {
        var properties = this.properties.get();
        if (properties == null) {
            properties = new EmojiProperties();
            properties.loadEmojiProperties();
            LOGGER.info("emoji properties {}", properties);
            this.properties.set(properties);
        }

        var path = getArchivePath();
        if (Files.exists(path)) {
            // 存在するファイルのハッシュ値を求める
            try (var input = Files.newInputStream(path)) {
                var hash = computeHash(input);
                if (properties.getHash().equals(hash)) {
                    final var archivePath = getArchivePath();

                    // コピーしたアーカイブをロード
                    var zip = new ZipFile(archivePath.toFile());
                    zip.stream().map(ZipEntry::getName).forEach(name -> {
                        var hex = name.toLowerCase();
                        var index = hex.indexOf(".");
                        if (0 < index) {
                            hex = hex.substring(0, index);
                        }
                        hexAndName.put(hex, name);
                    });
                    zipFile.set(zip);

                    ready.set(true);
                    return true;
                }
            }
        }
        return false;
    }

    private void checkReady() {
        if (!isReady()) throw new IllegalStateException("not ready");
        if (closed) throw new IllegalStateException("closed");
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (zipFile.get() != null) {
            try {
                zipFile.get().close();
            } catch (IOException e) {
                LOGGER.error("error occurred on closing", e);
            }
        }
    }

    public static String computeHash(InputStream input) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("sha-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        try {
            var bytes = new byte[1024];
            int length;
            while ((length = input.read(bytes)) != -1) {
                digest.update(bytes, 0, length);
            }

            var hashBytes = digest.digest();
            var builder = new StringBuilder();
            // 1文字目の0が消えてしまうのでInteger.toHexStringじゃなくformatで
            for (var b : hashBytes)
                builder.append("%02x".formatted(Byte.toUnsignedInt(b)));
                //builder.append(Integer.toHexString(Byte.toUnsignedInt(b)));
            return builder.toString();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public EmojiProperties getProperties() { return properties.get(); }

    public ObjectProperty<Path> archivePathProperty() { return archivePath; }
    public Path getArchivePath() { return archivePath.get(); }
    public void setArchivePath(Path path) { archivePath.set(Objects.requireNonNull(path)); }

    public ReadOnlyBooleanProperty readyProperty() { return ready.getReadOnlyProperty(); }
    public boolean isReady() { return ready.get(); }
}
