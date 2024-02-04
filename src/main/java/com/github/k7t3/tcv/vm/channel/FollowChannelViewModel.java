package com.github.k7t3.tcv.vm.channel;

import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;
import javafx.scene.image.Image;

/**
 * フォローしているチャンネルの情報
 */
public class FollowChannelViewModel implements ViewModel {

    private static final double PROFILE_IMAGE_WIDTH = 32;
    private static final double PROFILE_IMAGE_HEIGHT = 32;

    private final TwitchChannel channel;

    private final ReadOnlyStringWrapper userId = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper userLogin = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();
    private final ReadOnlyObjectWrapper<Image> profileImage = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyBooleanWrapper live = new ReadOnlyBooleanWrapper();
    private ReadOnlyStringWrapper gameName = null;
    private ReadOnlyStringWrapper title = null;
    private ReadOnlyIntegerWrapper viewerCount = null;

    public FollowChannelViewModel(TwitchChannel channel) {
        this.channel = channel;
        update(channel);
    }

    public TwitchChannel getChannel() {
        return channel;
    }

    private void update(TwitchChannel channel) {
        var broadcaster = channel.getBroadcaster();
        userId.set(broadcaster.getUserId());
        userLogin.set(broadcaster.getUserLogin());
        userName.set(broadcaster.getUserName());
        broadcaster.getProfileImageUrl().ifPresent(url ->
                profileImage.set(
                        new Image(url, PROFILE_IMAGE_WIDTH, PROFILE_IMAGE_HEIGHT, true, true, true)
                )
        );

        if (channel.isStreaming()) {
            goOnline();
        }
    }

    public void goOnline() {
        this.live.set(true);
        var stream = channel.getStream();
        setGameName(stream.getGameName());
        setTitle(stream.getTitle());
        setViewerCount(stream.getViewerCount());
    }

    public void goOffline() {
        live.set(false);
        if (gameName != null) gameName.set(null);
        if (title != null) title.set(null);
        if (viewerCount != null) viewerCount.set(-1);
    }

    // ################################################################################
    // PROPERTIES

    public ReadOnlyStringProperty userIdProperty() { return userId.getReadOnlyProperty(); }
    public String getUserId() { return userId.get(); }

    public ReadOnlyStringProperty userLoginProperty() { return userLogin.getReadOnlyProperty(); }
    public String getUserLogin() { return userLogin.get(); }

    public ReadOnlyStringProperty userNameProperty() { return userName.getReadOnlyProperty(); }
    public String getUserName() { return userName.get(); }

    public ReadOnlyObjectProperty<Image> profileImageProperty() { return profileImage.getReadOnlyProperty(); }
    public Image getProfileImage() { return profileImage.get(); }

    public ReadOnlyBooleanProperty liveProperty() { return live.getReadOnlyProperty(); }
    public boolean isLive() { return live.get(); }

    private ReadOnlyStringWrapper gameNameWrapper() {
        if (gameName == null) gameName = new ReadOnlyStringWrapper();
        return gameName;
    }
    public ReadOnlyStringProperty gameNameProperty() { return gameNameWrapper().getReadOnlyProperty(); }
    private void setGameName(String gameName) { gameNameWrapper().set(gameName); }
    public String getGameName() { return gameName == null ? null : gameNameWrapper().get(); }

    private ReadOnlyStringWrapper titleWrapper() {
        if (title == null) title = new ReadOnlyStringWrapper();
        return title;
    }
    public ReadOnlyStringProperty titleProperty() { return titleWrapper().getReadOnlyProperty(); }
    private void setTitle(String title) { titleWrapper().set(title); }
    public String getTitle() { return title == null ? null : titleWrapper().get(); }

    private ReadOnlyIntegerWrapper viewerCountWrapper() {
        if (viewerCount == null) viewerCount = new ReadOnlyIntegerWrapper();
        return viewerCount;
    }
    public ReadOnlyIntegerProperty viewerCountProperty() { return viewerCountWrapper().getReadOnlyProperty(); }
    public int getViewerCount() { return viewerCount == null ? -1 : viewerCount.get(); }
    public void setViewerCount(int viewerCount) { viewerCountWrapper().set(viewerCount); }
}
