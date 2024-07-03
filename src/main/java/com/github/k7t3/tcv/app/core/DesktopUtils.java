package com.github.k7t3.tcv.app.core;

import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

public class DesktopUtils {

    public static void browse(String url) throws URISyntaxException, IOException {
        var uri = new URI(url);
        C.desktop.browse(uri);
    }

    public static void clipText(String text) {
        var cb = Clipboard.getSystemClipboard();
        cb.setContent(Map.of(DataFormat.PLAIN_TEXT, text));
    }

    public static void clipURL(String url) {
        var cb = Clipboard.getSystemClipboard();
        cb.setContent(Map.of(DataFormat.URL, url, DataFormat.PLAIN_TEXT, url));
    }

    private static class C {
        private static final Desktop desktop;
        static {
            desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                throw new RuntimeException("browse action not supported");
            }
        }
    }

}
