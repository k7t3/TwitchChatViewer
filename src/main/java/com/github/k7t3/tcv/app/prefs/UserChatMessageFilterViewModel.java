package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.chat.filter.UserChatMessageFilter;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.ChatMessageFilterPreferences;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class UserChatMessageFilterViewModel implements PreferencesViewModelBase {

    private final ObservableList<HideUserViewModel> users = FXCollections.observableArrayList();

    private final ChatMessageFilterPreferences filterPrefs;

    public UserChatMessageFilterViewModel() {
        var prefs = AppPreferences.getInstance();
        filterPrefs = prefs.getMessageFilterPreferences();

        var filter = filterPrefs.getUserChatMessageFilter();
        var users = filter.getUsers().stream().map(HideUserViewModel::new).toList();
        this.users.addAll(users);
    }

    public ObservableList<HideUserViewModel> getUsers() {
        return users;
    }

    @Override
    public boolean canSync() {
        return true;
    }

    @Override
    public void sync() {
        var filteredUsers = users.stream().map(HideUserViewModel::getAsFilteredUser).toList();
        var set = filterPrefs.getUserChatMessageFilter().getUsers();
        set.clear();
        set.addAll(filteredUsers);
        filterPrefs.writeToPreferences();
    }

    public class HideUserViewModel {
        private final StringProperty userId = new SimpleStringProperty();
        private final StringProperty userName = new SimpleStringProperty();
        private final StringProperty comment = new SimpleStringProperty();

        public HideUserViewModel(UserChatMessageFilter.FilteredUser user) {
            userId.set(user.userId());
            userName.set(user.userName());
            comment.set(user.comment());
        }

        public void remove() {
            users.remove(this);
        }

        public UserChatMessageFilter.FilteredUser getAsFilteredUser() {
            return new UserChatMessageFilter.FilteredUser(getUserId(), getUserName(), getComment());
        }

        public StringProperty userIdProperty() { return userId; }
        public String getUserId() { return userId.get(); }
        public void setUserId(String userId) { this.userId.set(userId); }

        public StringProperty userNameProperty() { return userName; }
        public String getUserName() { return userName.get(); }
        public void setUserName(String userName) { this.userName.set(userName); }

        public StringProperty commentProperty() { return comment; }
        public String getComment() { return comment.get(); }
        public void setComment(String comment) { this.comment.set(comment); }
    }

}
