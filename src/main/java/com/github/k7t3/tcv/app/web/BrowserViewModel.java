package com.github.k7t3.tcv.app.web;

import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.web.WebEngine;

public class BrowserViewModel implements ViewModel {

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36";

    static {
        // OpenJFX 21.0.2
        System.setProperty("com.sun.webkit.useHTTP2Loader", Boolean.FALSE.toString());
    }

    private final StringProperty url = new SimpleStringProperty();

    private final ObjectProperty<WebEngine> engine = new SimpleObjectProperty<>();

    public BrowserViewModel() {
        engine.addListener((ob, o, n) -> {
            if (n != null) {
                n.setUserAgent(USER_AGENT);
            }
        });
    }

    public void load() {
        var engine = getEngine();
        if (engine == null) return;

        var url = getUrl();
        if (url == null) return;

        engine.load(url);
    }

    public void clear() {
        var engine = getEngine();
        if (engine == null) return;

        engine.loadContent("");
    }

    // ******************** PROPERTIES ********************

    public StringProperty urlProperty() { return url; }
    public String getUrl() { return url.get(); }
    public void setUrl(String url) { this.url.set(url); }

    public ObjectProperty<WebEngine> engineProperty() { return engine; }
    public WebEngine getEngine() { return engineProperty().get(); }
    public void setEngine(WebEngine engine) { engineProperty().set(engine); }
}
