package com.github.k7t3.tcv.view.chat;

import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomViewModel;
import com.github.k7t3.tcv.app.chat.MergedChatRoomViewModel;
import com.github.k7t3.tcv.app.chat.SingleChatRoomViewModel;
import com.github.k7t3.tcv.view.core.FloatableStage;
import com.github.k7t3.tcv.view.core.Resources;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.DataFormat;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class ChatContainerView implements FxmlView<ChatRoomContainerViewModel>, Initializable {

    @FXML
    private HBox selectingPane;

    @FXML
    private Label selectingCountLabel;

    @FXML
    private Button mergeButton;

    @FXML
    private Button cancelButton;

    @FXML
    private BorderPane container;

    private GridPane chatContainer;

    @InjectViewModel
    private ChatRoomContainerViewModel viewModel;

    private Map<ChatRoomViewModel, Node> items;

    private Map<ChatRoomViewModel, FloatableStage> floatableStages;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        items = new HashMap<>();
        viewModel.getChatRoomList().addListener(this::chatChanged);

        floatableStages = new HashMap<>();
        viewModel.getFloatableChatRoomList().addListener(this::floatableChatChanged);

        chatContainer = new GridPane();
        container.setCenter(chatContainer);

        selectingCountLabel.textProperty().bind(
                viewModel.selectingCountProperty().asString(Resources.getString("container.selecting.count.format"))
        );
        selectingPane.managedProperty().bind(selectingPane.visibleProperty());
        selectingPane.visibleProperty().bind(viewModel.selectModeProperty());

        mergeButton.getStyleClass().addAll(Styles.ROUNDED, Styles.SMALL, Styles.ACCENT);
        mergeButton.disableProperty().bind(viewModel.selectingCountProperty().lessThan(2));
        mergeButton.setOnAction(e -> viewModel.mergeSelectedChats());

        cancelButton.getStyleClass().addAll(Styles.ROUNDED, Styles.SMALL);
        cancelButton.setOnAction(e -> viewModel.unselectAll());
    }

    private void floatableChatChanged(ListChangeListener.Change<? extends ChatRoomViewModel> c) {
        while (c.next()) {
            if (c.wasAdded()) {
                for (var chat : c.getAddedSubList())
                    floatableAdded(chat);
            }
            if (c.wasRemoved()) {
                for (var chat : c.getRemoved())
                    floatableRemoved(chat);
            }
        }
    }

    private void floatableAdded(ChatRoomViewModel chat) {
        var stage = floatableStages.get(chat);
        if (stage != null) return;

        // マージされたチャット
        if (chat instanceof MergedChatRoomViewModel mergedChatRoom) {

            var tuple = FluentViewLoader.fxmlView(FloatableMergedChatRoomView.class)
                    .resourceBundle(Resources.getResourceBundle())
                    .viewModel(mergedChatRoom)
                    .load();

            var view = tuple.getView();
            var behind = tuple.getCodeBehind();

            stage = behind.getFloatableStage();
            stage.setContent(view);
            stage.initOwner(container.getScene().getWindow());

        }

        // 単体のチャット
        else if (chat instanceof SingleChatRoomViewModel chatRoom) {

            var tuple = FluentViewLoader.fxmlView(FloatableSingleChatRoomView.class)
                    .resourceBundle(Resources.getResourceBundle())
                    .viewModel(chatRoom)
                    .load();

            var view = tuple.getView();
            var behind = tuple.getCodeBehind();

            stage = behind.getFloatableStage();
            stage.setContent(view);
            stage.initOwner(container.getScene().getWindow());

        }

        else {
            return;
        }

        floatableStages.put(chat, stage);
        stage.show();
    }

    private void floatableRemoved(ChatRoomViewModel chat) {
        var stage = floatableStages.get(chat);
        if (stage == null) return;

        stage.close();
        floatableStages.remove(chat);
    }

    private void chatChanged(ListChangeListener.Change<? extends ChatRoomViewModel> c) {
        while (c.next()) {
            if (c.wasAdded()) {
                for (var chat : c.getAddedSubList())
                    onAdded(chat);
            }
            if (c.wasRemoved()) {
                for (var chat : c.getRemoved())
                    onRemoved(chat);
            }
        }
    }

    private void onRemoved(ChatRoomViewModel chat) {
        var node = items.get(chat);
        if (node == null) return;

        chatContainer.getChildren().remove(node);
    }

    private void onAdded(ChatRoomViewModel chat) {

        // 通常のチャットビュー
        if (chat instanceof SingleChatRoomViewModel c2) {
            var tuple = FluentViewLoader.fxmlView(SingleChatRoomView.class)
                    .viewModel(c2)
                    .resourceBundle(Resources.getResourceBundle())
                    .load();

            var node = tuple.getView();

            var dragController = new DragController();
            dragController.installDragEvents(node);

            var columnCount = chatContainer.getColumnCount();
            GridPane.setFillWidth(node, true);
            GridPane.setFillHeight(node, true);
            GridPane.setHgrow(node, Priority.ALWAYS);
            GridPane.setVgrow(node, Priority.ALWAYS);
            chatContainer.addColumn(columnCount, node);

            tuple.getViewModel().joinChatAsync();

            items.put(chat, node);
        }

        // マージされたチャットビュー
        else if (chat instanceof MergedChatRoomViewModel merged) {
            var tuple = FluentViewLoader.fxmlView(MergedChatRoomView.class)
                    .viewModel(merged)
                    .resourceBundle(Resources.getResourceBundle())
                    .load();

            var node = tuple.getView();

            var dragController = new DragController();
            dragController.installDragEvents(node);

            var columnCount = chatContainer.getColumnCount();
            GridPane.setFillWidth(node, true);
            GridPane.setFillHeight(node, true);
            GridPane.setHgrow(node, Priority.ALWAYS);
            GridPane.setVgrow(node, Priority.ALWAYS);
            chatContainer.addColumn(columnCount, node);

            items.put(chat, node);
        }
    }

    private static class DragController {
        private static final DataFormat MOVE_DATA = new DataFormat("application/chat-view");

        private Timeline dragOverAnimation;

        public void installDragEvents(Node node) {
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
                dragOverAnimation.stop();
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
    }

}
