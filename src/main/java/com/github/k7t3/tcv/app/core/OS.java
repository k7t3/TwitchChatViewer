package com.github.k7t3.tcv.app.core;

public enum OS {

    LINUX,

    WINDOWS,

    MAC;

    private static final String OS_NAME = System.getProperty("os.name", "unknown").toLowerCase();

    private static OS os;

    public static OS current() {
        if (os == null) {
            if (isLinux())
                os = LINUX;
            else if (isWindows())
                os = WINDOWS;
            else
                os = MAC;
        }
        return os;
    }

    public static boolean isLinux() {
        return OS_NAME.startsWith("linux");
    }

    public static boolean isWindows() {
        return OS_NAME.startsWith("win");
    }

    public static boolean isMac() {
        return OS_NAME.startsWith("mac");
    }

}
