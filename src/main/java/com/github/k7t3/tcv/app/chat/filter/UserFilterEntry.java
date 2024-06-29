package com.github.k7t3.tcv.app.chat.filter;

import com.github.k7t3.tcv.domain.chat.ChatData;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Objects;
import java.util.function.Predicate;

public class UserFilterEntry implements Predicate<ChatData> {

    private final StringProperty userId;
    private final StringProperty userName;
    private final StringProperty comment;

    public UserFilterEntry(String userId, String userName, String comment) {
        this.userId = new SimpleStringProperty(userId);
        this.userName = new SimpleStringProperty(userName);
        this.comment = new SimpleStringProperty(comment);
    }

    @Override
    public boolean test(ChatData chatData) {
        return Objects.equals(chatData.userId(), getUserId());
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
