package com.github.k7t3.tcv.app.channel;

import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.app.service.TaskWorker;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.channel.FoundChannel;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;
import javafx.scene.image.Image;

import java.awt.*;
import java.net.URI;

public class FoundChannelViewModel implements ViewModel {

    private static final String CHANNEL_URL_FORMAT = "https://www.twitch.tv/%s";

    private static final double PROFILE_IMAGE_WIDTH = 32;
    private static final double PROFILE_IMAGE_HEIGHT = 32;

    private final ReadOnlyObjectWrapper<Broadcaster> broadcaster;

    private final ReadOnlyObjectWrapper<Image> profileImage;

    private final ReadOnlyBooleanWrapper live;

    private final ReadOnlyStringWrapper gameName;

    private final ChatRoomContainerViewModel chatContainer;

    private final ChannelViewModelRepository channelRepository;

    public FoundChannelViewModel(
            ChannelViewModelRepository channelRepository,
            ChatRoomContainerViewModel chatContainer,
            FoundChannel channel
    ) {
        broadcaster = new ReadOnlyObjectWrapper<>();
        profileImage = new ReadOnlyObjectWrapper<>();
        live = new ReadOnlyBooleanWrapper();
        gameName = new ReadOnlyStringWrapper();
        this.chatContainer = chatContainer;
        this.channelRepository = channelRepository;
        update(channel);
    }

    public void update(FoundChannel channel) {
        setBroadcaster(channel.getBroadcaster());
        setProfileImage(new Image(channel.getBroadcaster().getProfileImageUrl(), PROFILE_IMAGE_WIDTH, PROFILE_IMAGE_HEIGHT, true, true, true));
        setGameName(channel.getGameName());
        setLive(channel.isLive());
    }

    public FXTask<Boolean> openChannelPageOnBrowser() {
        var login = getBroadcaster().getUserLogin();

        var task = FXTask.task(() -> {
            var desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                return false;
            }

            desktop.browse(new URI(CHANNEL_URL_FORMAT.formatted(login)));
            return true;
        });

        TaskWorker.getInstance().submit(task);

        return task;
    }

    public FXTask<?> joinChatAsync() {
        var broadcaster = getBroadcaster();
        var t = channelRepository.getChannelAsync(broadcaster);
        t.setSucceeded(() -> chatContainer.register(t.getValue()));
        t.runAsync();
        return t;
    }

    // ******************** PROPERTIES ********************

    private ReadOnlyObjectWrapper<Broadcaster> broadcasterWrapper() { return broadcaster; }
    public ReadOnlyObjectProperty<Broadcaster> broadcasterProperty() { return broadcaster.getReadOnlyProperty(); }
    public Broadcaster getBroadcaster() { return broadcaster.get(); }
    private void setBroadcaster(Broadcaster broadcaster) { this.broadcaster.set(broadcaster); }

    private ReadOnlyObjectWrapper<Image> profileImageWrapper() { return profileImage; }
    public ReadOnlyObjectProperty<Image> profileImageProperty() { return profileImage.getReadOnlyProperty(); }
    public Image getProfileImage() { return profileImage.get(); }
    private void setProfileImage(Image profileImage) { this.profileImage.set(profileImage); }

    private ReadOnlyBooleanWrapper liveWrapper() { return live; }
    public ReadOnlyBooleanProperty liveProperty() { return live.getReadOnlyProperty(); }
    public boolean isLive() { return live.get(); }
    private void setLive(boolean live) { this.live.set(live); }

    private ReadOnlyStringWrapper gameNameWrapper() { return gameName; }
    public ReadOnlyStringProperty gameNameProperty() { return gameName.getReadOnlyProperty(); }
    public String getGameName() { return gameName.get(); }
    private void setGameName(String gameName) { this.gameName.set(gameName); }

}
