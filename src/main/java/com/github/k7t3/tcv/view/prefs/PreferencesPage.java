package com.github.k7t3.tcv.view.prefs;

import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.ViewModel;
import javafx.fxml.Initializable;
import javafx.scene.Node;

public interface PreferencesPage<T extends ViewModel> extends FxmlView<T>, Initializable {

    String getName();

    default Node getGraphic() {
        return null;
    }

}
