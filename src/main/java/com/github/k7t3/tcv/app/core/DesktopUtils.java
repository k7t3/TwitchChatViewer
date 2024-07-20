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
