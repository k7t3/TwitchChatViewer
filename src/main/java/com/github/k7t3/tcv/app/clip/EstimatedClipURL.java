package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;

import java.awt.*;
import java.net.URI;
import java.util.Map;

public record EstimatedClipURL(String url) {

    public void openBrowser() {
        var task = FXTask.task(() -> {
            var desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return false;
            }

            desktop.browse(new URI(url));
            return true;
        });

        TaskWorker.getInstance().submit(task);
    }

    public void copyURL() {
        var cb = Clipboard.getSystemClipboard();
        cb.setContent(Map.of(DataFormat.PLAIN_TEXT, url));
    }

}
