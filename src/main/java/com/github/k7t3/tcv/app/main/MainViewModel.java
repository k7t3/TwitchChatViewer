package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.app.core.AppHelper;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;

public class MainViewModel implements ViewModel {

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper(false);

    private final StringProperty footer = new SimpleStringProperty();

    private final ReadOnlyIntegerWrapper clipCount = new ReadOnlyIntegerWrapper();

    public MainViewModel() {
        var helper = AppHelper.getInstance();
        userName.bind(helper.userNameProperty());
        authorized.bind(helper.authorizedProperty());
    }

    public VideoClipPostListener createClipPostListener() {
        return new VideoClipPostListener(this);
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyStringWrapper userNameWrapper() { return userName; }
    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }

    private ReadOnlyBooleanWrapper authorizedWrapper() { return authorized; }
    public ReadOnlyBooleanProperty authorizedProperty() { return authorized.getReadOnlyProperty(); }
    public boolean isAuthorized() { return authorized.get(); }

    public StringProperty footerProperty() { return footer; }
    public String getFooter() { return footer.get(); }
    public void setFooter(String footer) { this.footer.set(footer); }

    ReadOnlyIntegerWrapper clipCountWrapper() { return clipCount; }
    public ReadOnlyIntegerProperty clipCountProperty() { return clipCount.getReadOnlyProperty(); }
    public int getClipCount() { return clipCount.get(); }
    void setClipCount(int clipCount) { this.clipCount.set(clipCount); }
}
