package com.github.k7t3.tcv.app.main;

import com.github.k7t3.tcv.app.channel.FollowChannelsViewModel;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import de.saxsys.mvvmfx.ViewModel;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;

import java.util.stream.Collectors;

public class MainViewModel implements ViewModel {

    private static final int NORM_STREAM_TITLE_LENGTH = 20;

    private final ReadOnlyStringWrapper userName = new ReadOnlyStringWrapper();

    private final StringProperty footer = new SimpleStringProperty();

    private final ReadOnlyIntegerWrapper clipCount = new ReadOnlyIntegerWrapper();

    public MainViewModel() {
        var helper = AppHelper.getInstance();
        userName.bind(helper.userNameProperty());

        // 認証解除されたらクリップ非表示
        helper.authorizedProperty().addListener((ob, o, n) -> {
            if (!n) {
                helper.getClipRepository().clear();
            }
        });

        clipCount.bind(helper.getClipRepository().getCountBinding());
    }

    public void installFollowChannelsViewModel(FollowChannelsViewModel followChannelsViewModel) {
        followChannelsViewModel.getSelectedChannels().addListener((ListChangeListener<? super TwitchChannelViewModel>) c -> {
            var list = c.getList();
            if (list.isEmpty())
                return;

            if (list.size() == 1) {
                setFooter(list.getFirst().getStreamTitle());
                return;
            }

            var titles = list.stream()
                    .map(this::normTitle)
                    .collect(Collectors.joining("/"));
            setFooter(titles);
        });
    }

    private String normTitle(TwitchChannelViewModel channel) {
        var title = channel.getStreamTitle();

        if (title.length() <= NORM_STREAM_TITLE_LENGTH) {
            return title;
        }

        return title.substring(0, NORM_STREAM_TITLE_LENGTH);
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
