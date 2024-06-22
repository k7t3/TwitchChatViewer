package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.AbstractViewModel;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import com.github.k7t3.tcv.entity.SaveType;
import com.github.k7t3.tcv.entity.service.ChannelGroupEntityService;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ChannelGroupRepository extends AbstractViewModel {

    private final ChannelGroupEntityService service;

    private final ObservableList<ChannelGroup> groups = FXCollections.observableArrayList(
            g -> new Observable[] { g.pinnedProperty(), g.nameProperty(), g.updatedAtProperty() }
    );

    public ChannelGroupRepository(ChannelGroupEntityService service) {
        this.service = service;
    }

    public List<String> retrieveAllChannelIds() {
        return service.retrieveAll().stream()
                .flatMap(entity -> entity.channelIds().stream())
                .distinct()
                .toList();
    }

    /**
     * チャンネルグループで使用しているチャンネルを与える
     * <p>
     *     {@link #retrieveAllChannelIds()}で事前に使用しているチャンネルを取得しておくこと
     * </p>
     */
    public void injectChannels(Map<String, TwitchChannelViewModel> channels) {
        var entities = service.retrieveAll();

        var groups = new HashSet<ChannelGroup>();

        for (var entity : entities) {
            var group = new ChannelGroup(entity.id());
            group.setName(entity.name());
            group.setComment(entity.comment());
            group.setCreatedAt(entity.createdAt());
            group.setUpdatedAt(entity.updatedAt());
            var groupChannels = entity.channelIds().stream().map(channels::get).toList();
            group.getChannels().addAll(groupChannels);
            groups.add(group);
        }

        this.groups.addAll(groups);
    }

    /**
     * リポジトリにロードされているすべてのグループを取得する
     * @return リポジトリにロードされているすべてのグループ
     */
    public ObservableList<ChannelGroup> getAll() {
        return groups;
    }

    /**
     * グループを保存する
     * @param group 保存するグループ
     */
    public FXTask<?> saveAsync(ChannelGroup group) {
        if (group == null) throw new IllegalArgumentException("group is null");

        SaveType saveType;
        var now = LocalDateTime.now();
        if (group.getId() == null) {
            saveType = SaveType.INSERT;
            group.generateId();
            group.setCreatedAt(now);
            group.setUpdatedAt(now);
            groups.add(group);
        } else {
            saveType = SaveType.UPDATE;
            group.setUpdatedAt(now);
        }

        var t = FXTask.task(() -> {
            var userIds = List.copyOf(group.getChannels()).stream()
                    .map(c -> c.getBroadcaster().getUserId())
                    .collect(Collectors.toSet());

            var entity = new ChannelGroupEntity(
                    group.getId(),
                    group.getName(),
                    group.getComment(),
                    group.isPinned(),
                    group.getCreatedAt(),
                    group.getUpdatedAt(),
                    userIds
            );
            service.save(saveType, entity);
        });
        t.runAsync();
        return t;
    }

    /**
     * グループを削除する
     * @param group 削除するグループ
     */
    public FXTask<?> removeAsync(ChannelGroup group) {
        if (group == null) throw new IllegalArgumentException("group is null");

        groups.remove(group);

        var t = FXTask.task(() -> {
            var entity = new ChannelGroupEntity(
                    group.getId(),
                    group.getName(),
                    group.getComment(),
                    group.isPinned(),
                    group.getCreatedAt(),
                    group.getUpdatedAt(),
                    Set.of()
            );
            service.remove(entity);
        });
        t.runAsync();
        return t;
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        // no-op
    }

    @Override
    public void onLogout() {
        groups.clear();
    }

    @Override
    public void close() {
        groups.clear();
    }

}
