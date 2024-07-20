package com.github.k7t3.tcv.app.core;

import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * アプリケーションのバージョン識別子
 */
public record Version(int major, int minor, int patch, boolean isDevelopmentVersion, String developmentCode) {

    private static final String DEFAULT_VERSION = "1.0.0-dev";

    private static Version getDefaultVersion() {
        return new Version(1, 0, 0, true, "dev");
    }

    /**
     * デフォルトの開発用バージョン識別子を返す
     * @return 開発用のバージョン識別子
     */
    public static Version of() {
        return of(System.getProperty("app.version", DEFAULT_VERSION));
    }

    /**
     * バージョン文字列をパースして返す
     * <p>
     *     パースに失敗したときは{@link Version#of()}を返す
     * </p>
     * @param version バージョン識別子
     * @return バージョン識別子
     */
    public static Version of(String version) {
        if (version == null || version.isEmpty())
            return getDefaultVersion();

        var tokens = version.split("[.-]");
        var iterator = Arrays.stream(tokens).iterator();

        int major = 0, minor = 0, patch = 0;
        boolean isDevVersion = false;
        String devCode = "";
        try {
            if (iterator.hasNext())
                major = Integer.parseUnsignedInt(iterator.next());

            if (iterator.hasNext())
                minor = Integer.parseUnsignedInt(iterator.next());

            if (iterator.hasNext())
                patch = Integer.parseUnsignedInt(iterator.next());

            if (iterator.hasNext()) {
                isDevVersion = true;
                devCode = iterator.next();
            }

            return new Version(major, minor, patch, isDevVersion, devCode);

        } catch (NumberFormatException ignore) {
            LoggerFactory.getLogger(Version.class).warn("failed to parse version [{}]", version);
            return getDefaultVersion();
        }
    }

    /**
     * X.Y.Z(-開発コード) 表記の文字列を返す
     * @return X.Y.Z(-開発コード) 表記の文字列
     */
    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (isDevelopmentVersion() ? "-" + developmentCode() : "");
    }

}
