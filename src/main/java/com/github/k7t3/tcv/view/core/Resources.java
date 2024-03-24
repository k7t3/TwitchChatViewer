package com.github.k7t3.tcv.view.core;

import javafx.scene.image.Image;

import java.util.*;

public class Resources {

    private static final ResourceBundle RESOURCE_BUNDLE;

    private static final TreeMap<Integer, Image> ICONS;

    static {
        RESOURCE_BUNDLE = ResourceBundle.getBundle("com.github.k7t3.tcv.tcv");
        ICONS = new TreeMap<>(
                Map.of(
                        16, smallerIcon(16),
                        32, smallerIcon(32),
                        128, loadIcon(128),
                        256, loadIcon(256),
                        512, loadIcon(512)
                )
        );
    }

    private static Image smallerIcon(double size) {
        return new Image(
                Objects.requireNonNull(Resources.class.getResource("/icons/twitch-chat-viewer-32.png")).toExternalForm(),
                size, size, true, true);
    }

    private static Image loadIcon(double size) {
        return new Image(
                Objects.requireNonNull(Resources.class.getResource("/icons/twitch-chat-viewer-512.png")).toExternalForm(),
                size, size, true, true);
    }

    public static List<Image> getIcons() {
        return ICONS.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getKey))
                .map(Map.Entry::getValue)
                .toList();
    }

    public static ResourceBundle getResourceBundle() {
        return RESOURCE_BUNDLE;
    }

    public static String getString(String key) {
        return RESOURCE_BUNDLE.getString(key);
    }

}
