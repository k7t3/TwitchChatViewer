package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.app.core.AppHelper;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

public class MainViewModel implements ViewModel {

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper(false);

    public MainViewModel() {
        var helper = AppHelper.getInstance();
        userName.bind(helper.userNameProperty());
        authorized.bind(helper.authorizedProperty());
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyStringWrapper userNameWrapper() { return userName; }
    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }

    private ReadOnlyBooleanWrapper authorizedWrapper() { return authorized; }
    public ReadOnlyBooleanProperty authorizedProperty() { return authorized.getReadOnlyProperty(); }
    public boolean isAuthorized() { return authorized.get(); }

}
