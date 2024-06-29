package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.chat.ClipChatMessage;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;

import java.awt.*;
import java.net.URI;
import java.util.Map;

public class EstimatedClipViewModel extends AbstractPostedClip {

    private final PostedClipRepository repository;

    private final ReadOnlyStringWrapper url;

    private final ReadOnlyStringWrapper id;

    public EstimatedClipViewModel(PostedClipRepository repository, ClipChatMessage clipMessage) {
        this.repository = repository;
        this.url = new ReadOnlyStringWrapper(clipMessage.getEstimatedURL());
        this.id = new ReadOnlyStringWrapper(clipMessage.getId());
    }

    @Override
    public void remove() {
        var url = getUrl();
        repository.getEstimatedClipURLs().remove(url);
    }

    @Override
    public FXTask<Boolean> openClipPageOnBrowser() {
        var url = getUrl();

        var task = FXTask.task(() -> {
            var desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return false;
            }

            desktop.browse(new URI(url));
            return true;
        });
        task.runAsync();

        return task;
    }

    @Override
    public void copyClipURL() {
        var cb = Clipboard.getSystemClipboard();
        cb.setContent(Map.of(DataFormat.PLAIN_TEXT, url));
    }

    public ReadOnlyStringProperty urlProperty() { return url.getReadOnlyProperty(); }
    public String getUrl() { return url.get(); }

    public ReadOnlyStringProperty idProperty() { return id.getReadOnlyProperty(); }
    public String getId() { return id.get(); }

}
