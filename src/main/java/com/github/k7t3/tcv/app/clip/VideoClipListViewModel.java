package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.clip.VideoClipRepository;
import de.saxsys.mvvmfx.ViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

public class VideoClipListViewModel implements ViewModel {

    private final ObservableList<VideoClipViewModel> clips = FXCollections.observableArrayList();

    private final FilteredList<VideoClipViewModel> filtered = new FilteredList<>(clips);

    private final SortedList<VideoClipViewModel> sorted = new SortedList<>(filtered);

    private ObservableList<Broadcaster> channelOwners;

    private VideoClipRepository repository;

    private MainViewModel mainViewModel;

    public VideoClipListViewModel() {
        sorted.setComparator((a, b) ->
                b.getPosted().getLatestTime().compareTo(a.getPosted().getLatestTime()));
    }

    public void installRepository(VideoClipRepository repository) {
        this.repository = repository;

        clips.addAll(
                repository.getClips().stream().map(c -> new VideoClipViewModel(this, c)).toList()
        );

        channelOwners = FXCollections.observableArrayList();
        channelOwners.addAll(repository.getChannelOwners());
    }

    public void installMainViewModel(MainViewModel mainViewModel) {
        this.mainViewModel = mainViewModel;
    }

    void onRemoved(VideoClipViewModel clip) {
        clips.remove(clip);
        mainViewModel.updateClipCount();
    }

    public void filter(Broadcaster channelOwner) {
        if (channelOwner == null) {
            filtered.setPredicate(null);
        } else {
            filtered.setPredicate(c -> c.getPosted().isPosted(channelOwner));
        }
    }

    public ObservableList<Broadcaster> getChannelOwners() {
        return channelOwners;
    }

    public ObservableList<VideoClipViewModel> getClips() {
        return sorted;
    }

    // ******************** PROPERTIES ********************

}
