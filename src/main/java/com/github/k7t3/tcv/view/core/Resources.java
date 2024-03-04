package com.github.k7t3.tcv.view.core;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;

import java.util.Objects;
import java.util.ResourceBundle;

public class Resources {

    private static final ResourceBundle RESOURCE_BUNDLE;

    private static final ObservableList<Image> ICONS;

    static {
        RESOURCE_BUNDLE = ResourceBundle.getBundle("com.github.k7t3.tcv.tcv");
        ICONS = FXCollections.observableArrayList(
                loadIcon(16),
                loadIcon(32),
                loadIcon(128),
                loadIcon(256),
                loadIcon(512)
        );
    }

    private static Image loadIcon(double size) {
        return new Image(
                Objects.requireNonNull(Resources.class.getResource("/icons/twitch-chat-viewer-512.png")).toExternalForm(),
                16, 16, true, true, true);
    }

    public static ObservableList<Image> getIcons() {
        return ICONS;
    }

    public static ResourceBundle getResourceBundle() {
        return RESOURCE_BUNDLE;
    }

    public static String getString(String key) {
        return RESOURCE_BUNDLE.getString(key);
    }

}
