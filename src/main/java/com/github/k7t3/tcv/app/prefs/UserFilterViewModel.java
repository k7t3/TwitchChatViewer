package com.github.k7t3.tcv.app.prefs;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.chat.filter.UserFilterEntry;
import com.github.k7t3.tcv.app.core.EditableViewModelBase;
import javafx.beans.Observable;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

public class UserFilterViewModel implements PreferencesViewModelBase {

    private final ObservableList<Wrapper> userEntries;
    private final FilteredList<Wrapper> filtered;

    public UserFilterViewModel(ChatFilters chatFilters) {
        this.userEntries = FXCollections.observableArrayList(w -> new Observable[] { w.removedProperty() });
        userEntries.addAll(chatFilters.getUserEntries()
                .stream()
                .map(e -> new UserFilterViewModel.Wrapper(e, chatFilters))
                .toList());
        filtered = new FilteredList<>(userEntries);
    }

    public ObservableList<Wrapper> getUsers() {
        return filtered;
    }

    @Override
    public boolean canSync() {
        return userEntries.stream().noneMatch(EditableViewModelBase::isInvalid);
    }

    @Override
    public void sync() {
        userEntries.stream().filter(EditableViewModelBase::isDirty).forEach(Wrapper::save);
    }

    public static class Wrapper extends EditableViewModelBase {
        private final UserFilterEntry entry;
        private final ChatFilters chatFilters;

        private final ReadOnlyStringWrapper userId = readOnlyStringWrapper();
        private final ReadOnlyStringWrapper userName = readOnlyStringWrapper();
        private final StringProperty comment = stringProperty();

        public Wrapper(UserFilterEntry entry, ChatFilters chatFilters) {
            this.entry = entry;
            this.chatFilters = chatFilters;
            userId.set(entry.getUserId());
            userName.set(entry.getUserName());
            comment.set(entry.getComment());
        }

        private void save() {
            if (!isDirty()) return;
            if (isRemoved()) {
                chatFilters.deleteUserFilter(entry);
                return;
            }

            if (!isInvalid()) {
                entry.setComment(getComment());
                chatFilters.saveUserFilter(entry);
            }
        }

        public ReadOnlyStringProperty userIdProperty() { return userId.getReadOnlyProperty(); }
        public String getUserId() { return userId.get(); }

        public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
        public String getUserName() { return userName.get(); }

        public StringProperty commentProperty() { return comment; }
        public String getComment() { return comment.get(); }
        public void setComment(String comment) { this.comment.set(comment); }
    }

}
