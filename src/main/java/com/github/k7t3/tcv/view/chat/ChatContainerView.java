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

package com.github.k7t3.tcv.view.chat;

import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomViewModel;
import com.github.k7t3.tcv.app.chat.MergedChatRoomViewModel;
import com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.prefs.ChatPreferences;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.view.core.FloatableStage;
import com.github.k7t3.tcv.view.core.ViewResources;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import de.saxsys.mvvmfx.ViewModel;
import java.net.URL;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.input.DataFormat;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;


public class ChatContainerView implements FxmlView<ChatRoomContainerViewModel>, Initializable, AutoCloseable {
    private final ViewResources owned = new ViewResources();
    @FunctionalInterface interface Loader {
        LoadedChat load(ChatRoomViewModel room, boolean floating, ViewResources owner);
    }
    private final Loader loader;

    /** 親のサービスを子画面へ渡す。窓位置保存はポップアウトの生成時に取得する。 */
    public ChatContainerView(ChannelGroupRepository groups, ChatPreferences preferences, Supplier<WindowBoundsService> bounds) {
        Objects.requireNonNull(groups);
        Objects.requireNonNull(preferences);
        Objects.requireNonNull(bounds);
        loader = (room, floating, owner) -> loadChat(room, floating, owner, groups, preferences, bounds);
    }
    ChatContainerView(Loader loader) { this.loader = loader; }
    ChatContainerView(BiFunction<ChatRoomViewModel, Boolean, LoadedChat> loader) { this.loader = (room, floating, owner) -> loader.apply(room, floating); }
    @Override public void close() { owned.close(); }

    record LoadedChat(Parent view, Runnable release, FloatableStage stage) implements AutoCloseable {
        @Override public void close() { release.run(); }
    }

    private static final class Opened implements AutoCloseable {
        final ViewResources owned = new ViewResources();
        @Override public void close() { owned.close(); }
    }


    @FXML
    private HBox selectingPane;

    @FXML
    private Label selectingCountLabel;

    @FXML
    private Button selectAllButton;

    @FXML
    private Button mergeButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button closeButton;

    @FXML
    private StackPane contentStack;

    @FXML
    private VBox loadStatePane;

    @FXML
    private ProgressIndicator loadProgress;

    @FXML
    private Label loadMessage;

    @FXML
    private Button retryLoadButton;

    private GridPane chatContainer;

    @InjectViewModel
    private ChatRoomContainerViewModel viewModel;

    private final Map<ChatRoomViewModel, Opened> items = new IdentityHashMap<>();
    private final Map<ChatRoomViewModel, Opened> floating = new IdentityHashMap<>();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        owned.add(() -> {
            var children = new ArrayList<>(items.values()); children.addAll(floating.values());
            items.clear(); floating.clear();
            CloseActions.runAll(children.stream().map(child -> (Runnable) child::close).toArray(Runnable[]::new));
        });
        owned.add(() -> {
            loadStatePane.visibleProperty().unbind(); loadStatePane.managedProperty().unbind();
            loadProgress.visibleProperty().unbind(); loadProgress.managedProperty().unbind(); loadMessage.textProperty().unbind();
            retryLoadButton.visibleProperty().unbind(); retryLoadButton.managedProperty().unbind(); retryLoadButton.setOnAction(null);
            selectingCountLabel.textProperty().unbind(); selectingPane.managedProperty().unbind(); selectingPane.visibleProperty().unbind();
            mergeButton.disableProperty().unbind();
            selectAllButton.setOnAction(null); mergeButton.setOnAction(null); closeButton.setOnAction(null); cancelButton.setOnAction(null);
        });

        chatContainer = new GridPane();
        contentStack.getChildren().addFirst(chatContainer);
        loadStatePane.visibleProperty().bind(viewModel.loadingProperty().or(viewModel.errorProperty().isNotEmpty()));
        loadStatePane.managedProperty().bind(loadStatePane.visibleProperty());
        loadProgress.visibleProperty().bind(viewModel.loadingProperty());
        loadProgress.managedProperty().bind(loadProgress.visibleProperty());
        loadMessage.textProperty().bind(Bindings.when(viewModel.loadingProperty())
                .then(Resources.getString("chat.load.loading")).otherwise(viewModel.errorProperty()));
        retryLoadButton.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        retryLoadButton.managedProperty().bind(retryLoadButton.visibleProperty());
        retryLoadButton.setOnAction(event -> viewModel.loadAsync());

        selectingCountLabel.textProperty().bind(
                viewModel.selectingCountProperty().asString(Resources.getString("container.selecting.count.format"))
        );
        selectingPane.managedProperty().bind(selectingPane.visibleProperty());
        selectingPane.visibleProperty().bind(viewModel.selectModeProperty());

        selectAllButton.getStyleClass().addAll(Styles.ROUNDED, Styles.SMALL);
        selectAllButton.setOnAction(e -> viewModel.selectAll());

        mergeButton.getStyleClass().addAll(Styles.ROUNDED, Styles.SMALL, Styles.ACCENT);
        mergeButton.disableProperty().bind(viewModel.selectingCountProperty().lessThan(2));
        mergeButton.setOnAction(e -> viewModel.mergeSelectedChats());

        closeButton.getStyleClass().addAll(Styles.ROUNDED, Styles.SMALL, Styles.DANGER);
        closeButton.setOnAction(e -> viewModel.closeAll());

        cancelButton.getStyleClass().addAll(Styles.ROUNDED, Styles.SMALL);
        cancelButton.setOnAction(e -> viewModel.unselectAll());

        ListChangeListener<ChatRoomViewModel> chatsChanged = change -> changed(change, false);
        ListChangeListener<ChatRoomViewModel> floatingChanged = change -> changed(change, true);
        viewModel.getChatRoomList().addListener(chatsChanged);
        owned.add(() -> viewModel.getChatRoomList().removeListener(chatsChanged));
        viewModel.getFloatingChatRoomList().addListener(floatingChanged);
        owned.add(() -> viewModel.getFloatingChatRoomList().removeListener(floatingChanged));
        for (var chat : List.copyOf(viewModel.getChatRoomList())) added(chat, false);
        for (var chat : List.copyOf(viewModel.getFloatingChatRoomList())) added(chat, true);

    }

    private void changed(ListChangeListener.Change<? extends ChatRoomViewModel> change, boolean floatable) {
        if (owned.isClosed()) return;
        while (change.next()) {
            for (var chat : List.copyOf(change.getRemoved())) removed(chat, floatable);
            for (var chat : List.copyOf(change.getAddedSubList())) added(chat, floatable);
        }
    }

    private void removed(ChatRoomViewModel chat, boolean floatable) {
        var child = (floatable ? floating : items).remove(chat);
        if (child != null) child.close();
    }

    private boolean current(ChatRoomViewModel chat, boolean floatable, Opened child) {
        return !owned.isClosed() && !child.owned.isClosed() && (floatable ? floating : items).get(chat) == child
                && (floatable ? viewModel.getFloatingChatRoomList() : viewModel.getChatRoomList()).contains(chat);
    }

    private void added(ChatRoomViewModel chat, boolean floatable) {
        if (owned.isClosed()) return;
        var children = floatable ? floating : items;
        if (children.containsKey(chat)) return;
        var child = new Opened(); children.put(chat, child);
        try {
            var loaded = loader.load(chat, floatable, child.owned);
            if (loaded == null) { children.remove(chat, child); child.close(); return; }
            child.owned.add(loaded::close);
            if (!current(chat, floatable, child)) { child.close(); return; }
            var node = loaded.view();
            if (floatable) {
                var stage = loaded.stage();
                stage.setContent(node); stage.setTitle("Twitch Chat Viewer - Floating Window"); stage.getIcons().setAll(Resources.getIcons());
                if (current(chat, true, child)) stage.show();
            } else {
                var drag = new DragController(); drag.installDragEvents(node); child.owned.add(drag::close);
                child.owned.add(() -> chatContainer.getChildren().remove(node));
                GridPane.setFillWidth(node, true); GridPane.setFillHeight(node, true);
                GridPane.setHgrow(node, Priority.ALWAYS); GridPane.setVgrow(node, Priority.ALWAYS);
                chatContainer.addColumn(chatContainer.getColumnCount(), node);
                if (current(chat, false, child) && chat instanceof SingleChatRoomViewModel single) single.joinChatAsync();
            }
        } catch (RuntimeException | Error failure) {
            children.remove(chat, child);
            CloseActions.runAll(() -> { if (failure instanceof RuntimeException exception) throw exception; throw (Error) failure; }, child::close);
        }
    }

    private static LoadedChat loadChat(ChatRoomViewModel chat, boolean floating, ViewResources owner,
                                       ChannelGroupRepository groups, ChatPreferences preferences, Supplier<WindowBoundsService> bounds) {
        if (chat instanceof SingleChatRoomViewModel single) {
            if (floating) return load(FloatableSingleChatRoomView.class, new FloatableSingleChatRoomView(groups, preferences, bounds.get()), single, owner);
            return load(SingleChatRoomView.class, new SingleChatRoomView(groups), single, owner);
        }
        if (chat instanceof MergedChatRoomViewModel merged) {
            if (floating) return load(FloatableMergedChatRoomView.class, new FloatableMergedChatRoomView(groups, preferences, bounds.get()), merged, owner);
            return load(MergedChatRoomView.class, new MergedChatRoomView(groups), merged, owner);
        }
        return null;
    }

    static <M extends ViewModel, C extends FxmlView<M> & AutoCloseable> LoadedChat load(Class<C> type, C controller, M model, ViewResources owner) {
        owner.add(() -> release(controller));
        if (owner.isClosed()) return null;
        var tuple = FluentViewLoader.fxmlView(type).codeBehind(controller).viewModel(model).resourceBundle(Resources.getResourceBundle()).load();
        FloatableStage stage = controller instanceof FloatableSingleChatRoomView single ? single.getFloatableStage()
                : controller instanceof FloatableMergedChatRoomView merged ? merged.getFloatableStage() : null;
        return new LoadedChat(tuple.getView(), () -> {}, stage);
    }

    private static void release(AutoCloseable controller) {
        try { controller.close(); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("chat view release failed", failure); }
    }

    private static class DragController implements AutoCloseable {
        private Node node;
        private static final DataFormat MOVE_DATA = new DataFormat("application/chat-view");

        private Timeline dragOverAnimation;

        public void installDragEvents(Node node) {
            this.node = node;
            // ドラッグの開始
            node.setOnDragDetected(e -> {

                if (e.getButton() != MouseButton.PRIMARY)
                    return;

                var dragBoard = node.startDragAndDrop(TransferMode.MOVE);

                // 現在のカラム位置を埋め込む
                var column = GridPane.getColumnIndex(node);
                dragBoard.setContent(Map.of(MOVE_DATA, column));

                var view = node.snapshot(null, null);
                dragBoard.setDragView(view, e.getX(), e.getY());

                e.consume();
            });

            // ドラッグ領域に入った
            node.setOnDragEntered(e -> {
                dragOverAnimation = Animations.flash(node);
                dragOverAnimation.setCycleCount(Integer.MAX_VALUE);
                dragOverAnimation.playFromStart();
            });

            // ドラッグ領域から出た
            node.setOnDragExited(e -> {
                if (dragOverAnimation != null) dragOverAnimation.stop();
                dragOverAnimation = null;
            });

            // ドラッグの受け入れ
            node.setOnDragOver(e -> {

                var dragBoard = e.getDragboard();

                if (!dragBoard.hasContent(MOVE_DATA)) {
                    return;
                }

                e.acceptTransferModes(TransferMode.MOVE);

                e.consume();

            });

            // ドロップ
            node.setOnDragDropped(e -> {

                var dragBoard = e.getDragboard();

                // ドラッグ元のカラムを取得
                var fromColumn = (int) dragBoard.getContent(MOVE_DATA);

                // 現在のカラム位置
                var column = GridPane.getColumnIndex(node);

                // カラムを移動する
                GridPane.setColumnIndex(node, fromColumn);

                // ドラッグボードの値を更新
                dragBoard.setContent(Map.of(MOVE_DATA, column));

                e.setDropCompleted(true);

                e.consume();

            });

            // ドロップが正常に終了した
            node.setOnDragDone(e -> {

                var dragBoard = e.getDragboard();

                // ドロップ先のカラムを取得(DragBoardを更新してくれているはず)
                var toColumn = (int) dragBoard.getContent(MOVE_DATA);

                // ドラッグボードをクリア
                dragBoard.clear();

                GridPane.setColumnIndex(node, toColumn);

            });
        }
        @Override public void close() {
            if (dragOverAnimation != null) { dragOverAnimation.stop(); dragOverAnimation = null; }
            if (node == null) return;
            node.setOnDragDetected(null); node.setOnDragEntered(null); node.setOnDragExited(null);
            node.setOnDragOver(null); node.setOnDragDropped(null); node.setOnDragDone(null); node = null;
        }
    }

}
