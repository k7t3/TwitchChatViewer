package com.github.k7t3.tcv.view.clip;

import com.github.k7t3.tcv.app.clip.EstimatedClipURL;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.web.BrowserViewModel;
import com.github.k7t3.tcv.view.core.Resources;
import com.github.k7t3.tcv.view.web.BrowserController;
import javafx.scene.control.*;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public class EstimatedClipURLCell extends ListCell<EstimatedClipURL> {

    private Hyperlink hyperlink = null;

    private final PostedClipRepository repository;

    private final BrowserController controller;

    public EstimatedClipURLCell(PostedClipRepository repository, BrowserController controller) {
        this.repository = repository;
        this.controller = controller;
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void initGraphic() {
        if (hyperlink != null) return;

        hyperlink = new Hyperlink();
        hyperlink.setOnAction(e -> {
            controller.load(getItem().url());
            controller.show();
        });

        var openBrowser = new MenuItem(
                Resources.getString("clip.open.browser"),
                FontIcon.of(Feather.GLOBE)
        );
        openBrowser.setOnAction(e -> getItem().openBrowser());

        var copyURL = new MenuItem(
                Resources.getString("clip.copy.link"),
                FontIcon.of(Feather.COPY)
        );
        copyURL.setOnAction(e -> getItem().copyURL());

        var removeMenuItem = new MenuItem(
                Resources.getString("clip.remove"),
                FontIcon.of(Feather.TRASH)
        );
        removeMenuItem.setOnAction(e ->
                repository.getEstimatedClipURLs().remove(getItem()));

        hyperlink.setContextMenu(new ContextMenu(
                openBrowser,
                copyURL,
                new SeparatorMenuItem(),
                removeMenuItem
        ));
    }

    @Override
    protected void updateItem(EstimatedClipURL s, boolean b) {
        super.updateItem(s, b);

        if (s == null || b) {
            setGraphic(null);
            return;
        }

        if (hyperlink == null) {
            initGraphic();
        }

        hyperlink.setText(s.url());
        hyperlink.setVisited(false);
        setGraphic(hyperlink);
    }
}
