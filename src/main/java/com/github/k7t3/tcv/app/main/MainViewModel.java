package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.app.core.AppHelper;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;

public class MainViewModel implements ViewModel {

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private final StringProperty footer = new SimpleStringProperty();

    private final ReadOnlyIntegerWrapper clipCount = new ReadOnlyIntegerWrapper();

    public MainViewModel() {
        var helper = AppHelper.getInstance();
        userName.bind(helper.userNameProperty());

        // 認証解除されたらクリップ非表示
        helper.authorizedProperty().addListener((ob, o, n) -> {
            if (!n) {
                setClipCount(0);
            }
        });
    }

    public void updateClipCount() {
        var helper = AppHelper.getInstance();
        var repo = helper.getTwitch().getClipRepository();
        setClipCount(repo.getClipCount());
    }

    public VideoClipPostListener createClipPostListener() {
        return new VideoClipPostListener(this);
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyStringWrapper userNameWrapper() { return userName; }
    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }

    public StringProperty footerProperty() { return footer; }
    public String getFooter() { return footer.get(); }
    public void setFooter(String footer) { this.footer.set(footer); }

    ReadOnlyIntegerWrapper clipCountWrapper() { return clipCount; }
    public ReadOnlyIntegerProperty clipCountProperty() { return clipCount.getReadOnlyProperty(); }
    public int getClipCount() { return clipCount.get(); }
    private void setClipCount(int clipCount) { this.clipCount.set(clipCount); }
}
