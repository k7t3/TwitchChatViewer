package com.github.k7t3.tcv.app.core;

import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public record Version(int major, int minor, int patch) {

    private static final String DEFAULT_VERSION = "1.0.0";

    private static Version getDefaultVersion() {
        return new Version(1, 0, 0);
    }

    public static Version of() {
        return of(System.getProperty("app.version", DEFAULT_VERSION));
    }

    public static Version of(String version) {
        if (version == null || version.isEmpty())
            return getDefaultVersion();

        var tokens = version.split("\\.");
        var iterator = Arrays.stream(tokens).iterator();

        int major = 0, minor = 0, patch = 0;
        try {
            if (iterator.hasNext())
                major = Integer.parseUnsignedInt(iterator.next());

            if (iterator.hasNext())
                minor = Integer.parseUnsignedInt(iterator.next());

            if (iterator.hasNext())
                patch = Integer.parseUnsignedInt(iterator.next());

            return new Version(major, minor, patch);

        } catch (NumberFormatException ignore) {
            LoggerFactory.getLogger(Version.class).warn("failed to parse version [{}]", version);
            return getDefaultVersion();
        }
    }

    /**
     * X.Y.Z 表記の文字列を返す
     * @return X.Y.Z 表記の文字列
     */
    @Override
    public String toString() {
        return IntStream.of(major, minor, patch)
                .mapToObj(Integer::toString)
                .collect(Collectors.joining("."));
    }

}
