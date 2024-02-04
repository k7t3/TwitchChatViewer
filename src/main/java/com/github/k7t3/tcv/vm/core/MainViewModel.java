package com.github.k7t3.tcv.vm.core;

import de.saxsys.mvvmfx.SceneLifecycle;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;

public class MainViewModel implements ViewModel, SceneLifecycle {

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper(false);

    private final StringProperty searchWord = new SimpleStringProperty();

    public MainViewModel() {
    }

    @Override
    public void onViewAdded() {
    }

    @Override
    public void onViewRemoved() {
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyStringWrapper userNameWrapper() { return userName; }
    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }
    public void setUserName(String userName) { this.userName.set(userName); }

    private ReadOnlyBooleanWrapper authorizedWrapper() { return authorized; }
    public ReadOnlyBooleanProperty authorizedProperty() { return authorized.getReadOnlyProperty(); }
    public boolean isAuthorized() { return authorized.get(); }
    public void setAuthorized(boolean authorized) { this.authorized.set(authorized); }

    public StringProperty searchWordProperty() { return searchWord; }
    public String getSearchWord() { return searchWord.get(); }
    public void setSearchWord(String searchWord) { this.searchWord.set(searchWord); }

}
