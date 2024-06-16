package com.github.k7t3.tcv.app.core;

import java.nio.file.Path;

public enum OS {

    LINUX,

    WINDOWS,

    MAC;

    private static final String OS_NAME = System.getProperty("os.name", "unknown").toLowerCase();

    private static OS os;

    public static OS current() {
        if (os == null) {
            if (OS_NAME.startsWith("linux"))
                os = LINUX;
            else if (OS_NAME.startsWith("win"))
                os = WINDOWS;
            else if (OS_NAME.startsWith("mac"))
                os = MAC;
            else
                os = LINUX;
        }
        return os;
    }

    private Path applicationDirectory = null;

    public Path getApplicationDirectory() {
        if (applicationDirectory == null) {
            var parent = switch (this) {
                case LINUX -> Path.of(System.getProperty("user.home"), ".config");
                case WINDOWS -> Path.of(System.getProperty("user.home"), "AppData", "Roaming");
                case MAC -> Path.of(System.getProperty("user.home"), "Library", "Application Support");
            };
            applicationDirectory = parent.resolve("com.github.k7t3.tcv");
        }
        return applicationDirectory;
    }

    public static boolean isLinux() {
        return current() == LINUX;
    }

    public static boolean isWindows() {
        return current() == WINDOWS;
    }

    public static boolean isMac() {
        return current() == MAC;
    }

}
