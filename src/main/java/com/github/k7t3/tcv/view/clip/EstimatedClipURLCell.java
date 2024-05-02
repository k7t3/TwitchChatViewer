package com.github.k7t3.tcv.view.clip;

import com.github.k7t3.tcv.app.clip.EstimatedClipViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.web.BrowserController;
import javafx.scene.control.*;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

public class EstimatedClipURLCell extends ListCell<EstimatedClipViewModel> {

    private Hyperlink hyperlink = null;

    private final BrowserController controller;

    public EstimatedClipURLCell(BrowserController controller) {
        this.controller = controller;
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
    }

    private void initGraphic() {
        if (hyperlink != null) return;

        hyperlink = new Hyperlink();
        hyperlink.setOnAction(e -> {
            controller.load(getItem().getUrl());
            controller.show();
        });

        var openBrowser = new MenuItem(
                Resources.getString("clip.open.browser"),
                FontIcon.of(Feather.GLOBE)
        );
        openBrowser.setOnAction(e -> getItem().openClipPageOnBrowser());

        var copyURL = new MenuItem(
                Resources.getString("clip.copy.link"),
                FontIcon.of(Feather.COPY)
        );
        copyURL.setOnAction(e -> getItem().copyClipURL());

        var removeMenuItem = new MenuItem(
                Resources.getString("clip.remove"),
                FontIcon.of(Feather.TRASH)
        );
        removeMenuItem.setOnAction(e -> getItem().remove());

        hyperlink.setContextMenu(new ContextMenu(
                openBrowser,
                copyURL,
                new SeparatorMenuItem(),
                removeMenuItem
        ));
    }

    @Override
    protected void updateItem(EstimatedClipViewModel s, boolean b) {
        super.updateItem(s, b);

        if (s == null || b) {
            setGraphic(null);
            return;
        }

        if (hyperlink == null) {
            initGraphic();
        }

        hyperlink.setText(s.getUrl());
        hyperlink.setVisited(false);
        setGraphic(hyperlink);
    }
}
