package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.keyboard.KeyBindingCombinations;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.event.EventPublishers;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import com.github.k7t3.tcv.entity.service.WindowBoundsEntityService;
import javafx.beans.property.*;
import javafx.stage.Stage;

import java.io.Closeable;
import java.util.Objects;

public class AppHelper implements Closeable {

    private final ReadOnlyStringWrapper userId = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper();
    private final ObjectProperty<Twitch> twitch = new SimpleObjectProperty<>();

    private ObjectProperty<Stage> primaryStage;

    private final EventPublishers publishers = new EventPublishers();
    private final EventSubscribers subscribers = new EventSubscribers(publishers);

    private UserDataFile userDataFile;
    private ChannelGroupRepository channelGroupRepository;
    private WindowBoundsService windowBoundsService;
    private KeyBindingCombinations keyBindingCombinations;

    private AppHelper() {
        userId.bind(twitch.map(Twitch::getUserId));
        userName.bind(twitch.map(Twitch::getUserName));
        authorized.bind(twitch.isNotNull());
    }

    public void setUserDataFile(UserDataFile userDataFile) {
        this.userDataFile = userDataFile;
    }

    public UserDataFile getUserDataFile() {
        return userDataFile;
    }

    public ChannelGroupRepository getChannelGroupRepository() {
        if (channelGroupRepository == null) {
            var userDataFile = getUserDataFile();
            var groupService = new ChannelGroupEntityService(userDataFile.getConnector());
            channelGroupRepository = new ChannelGroupRepository(groupService);
        }
        return channelGroupRepository;
    }

    public WindowBoundsService getWindowBoundsService() {
        if (windowBoundsService == null) {
            var userDataFile = Objects.requireNonNull(getUserDataFile());
            var entityService = new WindowBoundsEntityService(userDataFile.getConnector());
            windowBoundsService = new WindowBoundsService(entityService);
        }
        return windowBoundsService;
    }

    public KeyBindingCombinations getKeyBindingCombinations() {
        if (keyBindingCombinations == null) {
            keyBindingCombinations = new KeyBindingCombinations();
        }
        return keyBindingCombinations;
    }

    public EventPublishers getPublishers() {
        return publishers;
    }

    public EventSubscribers getSubscribers() {
        return subscribers;
    }

    public FXTask<Void> logoutAsync() {
        if (!isAuthorized()) return FXTask.empty();

        final var twitch = getTwitch();
        setTwitch(null);

        var task = FXTask.task(twitch::logout);
        task.runAsync();
        return task;
    }

    @Override
    public void close() {
        if (userDataFile != null) {
            userDataFile.closeDatabase();
        }

        var twitch = getTwitch();
        if (twitch != null) {
            twitch.close();
        }

        var publishers = getPublishers();
        publishers.close();

        var worker = TaskWorker.getInstance();
        worker.close();
    }

    public static AppHelper getInstance() {
        return Holder.INSTANCE;
    }

    private static class Holder {
        private static final AppHelper INSTANCE = new AppHelper();
    }

    // ########################################
    // PROPERTIES

    public ReadOnlyStringProperty userIdProperty() { return userId.getReadOnlyProperty(); }
    public String getUserId() { return userId.get(); }

    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }

    public ReadOnlyBooleanProperty authorizedProperty() { return authorized.getReadOnlyProperty(); }
    public boolean isAuthorized() { return authorized.get(); }

    public ObjectProperty<Twitch> twitchProperty() { return twitch; }
    public Twitch getTwitch() { return twitch.get(); }
    public void setTwitch(Twitch twitch) { this.twitch.set(twitch); }

    public ObjectProperty<Stage> primaryStageProperty() {
        if (primaryStage == null) primaryStage = new SimpleObjectProperty<>();
        return primaryStage;
    }
    public Stage getPrimaryStage() { return primaryStageProperty().get(); }
    public void setPrimaryStage(Stage primaryStage) { primaryStageProperty().set(primaryStage); }

}
