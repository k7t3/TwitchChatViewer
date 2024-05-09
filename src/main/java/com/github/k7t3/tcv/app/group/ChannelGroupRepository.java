package com.github.k7t3.tcv.app.group;

import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.entity.ChannelGroupEntity;
import com.github.k7t3.tcv.entity.service.ChannelGroupService;
import com.github.k7t3.tcv.entity.SaveType;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ChannelGroupRepository {

    private final ChannelGroupService service;

    private final ChannelViewModelRepository repository;

    private final ObservableList<ChannelGroup> groups = FXCollections.observableArrayList(
            g -> new Observable[] { g.nameProperty(), g.updatedAtProperty() }
    );

    public ChannelGroupRepository(ChannelGroupService service, ChannelViewModelRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    /**
     * リポジトリに登録されているグループをすべてロードする
     */
    public FXTask<?> loadAll() {
        var task = FXTask.task(() -> {
            // サービスから取得できたすべてのグループ
            var entities = service.retrieveAll();

            // すべてのグループに関するチャンネルを取得する
            var channelIds = entities.stream().flatMap(e -> e.channelIds().stream()).toList();
            var channels = repository.getChannelsAsync(channelIds)
                    .get()
                    .stream()
                    .peek(c -> c.getChannel().setPersistent(true))
                    .collect(Collectors.toMap(c -> c.getBroadcaster().getUserId(), c -> c));

            var groups = new HashSet<ChannelGroup>();

            for (var entity : entities) {
                var group = new ChannelGroup(entity.id());
                group.setName(entity.name());
                group.setCreatedAt(entity.createdAt());
                group.setUpdatedAt(entity.updatedAt());
                var groupChannels = entity.channelIds().stream().map(channels::get).toList();
                group.getChannels().setAll(groupChannels);
                groups.add(group);
            }

            return groups;
        });
        task.setSucceeded(() -> groups.setAll(task.getValue()));
        task.runAsync();
        return task;
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
        if (group.getId() == null) {
            saveType = SaveType.INSERT;
            group.generateId();
            groups.add(group);
        } else {
            saveType = SaveType.UPDATE;
        }

        var t = FXTask.task(() -> {
            var userIds = List.copyOf(group.getChannels()).stream()
                    .map(c -> c.getBroadcaster().getUserId())
                    .collect(Collectors.toSet());

            var entity = new ChannelGroupEntity(
                    group.getId(),
                    group.getName(),
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
                    group.getCreatedAt(),
                    group.getUpdatedAt(),
                    Set.of()
            );
            service.remove(entity);
        });
        t.runAsync();
        return t;
    }

}
