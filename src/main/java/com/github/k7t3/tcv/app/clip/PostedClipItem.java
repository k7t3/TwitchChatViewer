/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.k7t3.tcv.app.clip;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.DesktopUtils;
import com.github.k7t3.tcv.app.demo.DEMOVideoClipProvider;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.channel.Broadcaster;
import com.github.k7t3.tcv.domain.chat.ClipChatMessage;
import com.github.k7t3.tcv.domain.chat.ClipFinder;
import com.github.k7t3.tcv.domain.clip.VideoClip;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class PostedClipItem {

    protected final ReadOnlyIntegerWrapper times = new ReadOnlyIntegerWrapper(0);
    protected final Set<Broadcaster> postedChannels = new HashSet<>();
    protected final ReadOnlyObjectWrapper<LocalDateTime> lastPostedAt = new ReadOnlyObjectWrapper<>();

    private final ReadOnlyStringWrapper id = new ReadOnlyStringWrapper();
    private final ReadOnlyStringWrapper url = new ReadOnlyStringWrapper();

    private ReadOnlyObjectWrapper<VideoClip> clip;

    private final PostedClipRepository repository;

    public PostedClipItem(PostedClipRepository repository, String id, String url, VideoClip clip) {
        if (!url.startsWith("https")) throw new IllegalArgumentException("illegal url: " + url);
        this.repository = repository;
        this.id.set(id);
        this.url.set(url);

        if (clip != null) {
            setClip(clip);
        }
    }

    public FXTask<?> retry() {
        var helper = AppHelper.getInstance();
        var finder = new ClipFinder(helper.getTwitch());
        var url = getUrl();

        var task = FXTask.task(() -> finder.findClip(url).flatMap(ClipChatMessage::getClip).orElse(null));
        return task.onDone(c -> {
            if (c != null) {
                setClip(c);
            }
        }).runAsync();
    }

    public void remove() {
        repository.getItems().remove(getId());
    }

    public FXTask<?> browseClipPageAsync() {
        return FXTask.task(() -> {
            DesktopUtils.browse(getUrl());
            return null;
        }).runAsync();
    }

    public void copyClipURL() {
        DesktopUtils.clipURL(getUrl());
    }

    public LazyImage getThumbnailImage() {
        return repository.getThumbnailStore().get(this);
    }

    public void onPosted(Broadcaster broadcaster) {
        postedChannels.add(broadcaster);
        lastPostedAt.set(LocalDateTime.now());
        times.set(times.get() + 1);
    }

    public boolean isPosted(Broadcaster broadcaster) {
        return postedChannels.contains(broadcaster);
    }

    public Set<Broadcaster> getPostedChannels() {
        return postedChannels;
    }

    public ReadOnlyStringProperty idProperty() { return id.getReadOnlyProperty(); }
    public String getId() { return id.get(); }

    public ReadOnlyStringProperty urlProperty() { return url.getReadOnlyProperty(); }
    public String getUrl() { return url.get(); }

    public ReadOnlyIntegerProperty timesProperty() { return times.getReadOnlyProperty(); }
    public int getTimes() { return times.get(); }

    private ReadOnlyObjectWrapper<VideoClip> clip() {
        if (clip == null) clip = new ReadOnlyObjectWrapper<>();
        return clip;
    }
    public ReadOnlyObjectProperty<VideoClip> clipProperty() { return clip().getReadOnlyProperty(); }
    public VideoClip getClip() { return clip == null ? null : clip.get(); }
    private void setClip(VideoClip clip) {
        clip().set(DEMOVideoClipProvider.provide(clip));
    }

    public ObservableValue<String> observableThumbnailLink() { return clip().map(VideoClip::thumbnailUrl); }
    String getThumbnailLink() { return observableThumbnailLink().getValue(); }


    public ReadOnlyObjectProperty<LocalDateTime> lastPostedAtProperty() { return lastPostedAt.getReadOnlyProperty(); }
    public LocalDateTime getLastPostedAt() { return lastPostedAt.get(); }

    public ObservableValue<String> observableTitle() { return clip().map(VideoClip::title); }
    public String getTitle() { return observableTitle().getValue(); }

    public ObservableValue<String> observableCreator() { return clip().map(VideoClip::creatorName); }
    public String getCreator() { return observableCreator().getValue(); }

}
