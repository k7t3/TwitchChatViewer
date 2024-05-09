package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.app.user.UserDataFile;
import com.github.k7t3.tcv.domain.Twitch;
import com.github.k7t3.tcv.domain.channel.ChannelRepository;
import com.github.k7t3.tcv.entity.service.ChannelGroupService;
import com.github.k7t3.tcv.prefs.AppPreferences;
import javafx.beans.property.*;
import javafx.stage.Stage;

import java.io.Closeable;

public class AppHelper implements Closeable {

    private final ReadOnlyStringWrapper userId = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();
    private final ReadOnlyBooleanWrapper authorized = new ReadOnlyBooleanWrapper();

    private final ObjectProperty<Twitch> twitch = new SimpleObjectProperty<>();

    private ObjectProperty<Stage> primaryStage;

    private PostedClipRepository clipRepository;

    private ChatRoomContainerViewModel containerViewModel;

    private ChannelViewModelRepository channelRepository;

    private UserDataFile userDataFile;

    private ChannelGroupRepository channelGroupRepository;

    private AppHelper() {
        userId.bind(twitch.map(Twitch::getUserId));
        userName.bind(twitch.map(Twitch::getUserName));
        authorized.bind(twitch.isNotNull());
    }

    public void setContainerViewModel(ChatRoomContainerViewModel containerViewModel) {
        this.containerViewModel = containerViewModel;
    }

    public PostedClipRepository getClipRepository() {
        if (clipRepository == null) {
            clipRepository = new PostedClipRepository();
        }
        return clipRepository;
    }

    public ChannelViewModelRepository getChannelRepository() {
        if (channelRepository == null) {
            channelRepository = new ChannelViewModelRepository(new ChannelRepository(getTwitch()));
        }
        return channelRepository;
    }

    public UserDataFile getUserDataFile() {
        if (userDataFile == null) {
            var prefs = AppPreferences.getInstance();
            var generalPrefs = prefs.getGeneralPreferences();
            userDataFile = new UserDataFile(generalPrefs.getUserDataFilePath());
        }
        return userDataFile;
    }

    public ChannelGroupRepository getChannelGroupRepository() {
        if (channelGroupRepository == null) {
            var userDataFile = getUserDataFile();
            var groupService = new ChannelGroupService(userDataFile.getConnector());
            var channelRepository = getChannelRepository();
            channelGroupRepository = new ChannelGroupRepository(groupService, channelRepository);
        }
        return channelGroupRepository;
    }

    public FXTask<Void> logout() {
        if (!isAuthorized()) return FXTask.empty();

        var task = FXTask.task(() -> getTwitch().logout());
        FXTask.setOnSucceeded(task, e -> onLogout());
        TaskWorker.getInstance().submit(task);
        return task;
    }

    private void onLogout() {
        if (channelRepository != null) {
            channelRepository.clear();
        }
        setTwitch(null);
    }

    @Override
    public void close() {
        var container = containerViewModel;
        if (container != null) {
            container.clearAll();
        }

        if (userDataFile != null) {
            userDataFile.closeDatabase();
        }

        var twitch = getTwitch();
        if (twitch != null) {
            twitch.close();
        }

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
