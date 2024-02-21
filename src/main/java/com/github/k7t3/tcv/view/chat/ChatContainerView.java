package com.github.k7t3.tcv.view.chat;

import atlantafx.base.util.Animations;
import com.github.k7t3.tcv.app.chat.ChatContainerViewModel;
import com.github.k7t3.tcv.app.chat.ChatViewModel;
import com.github.k7t3.tcv.view.core.Resources;
import de.saxsys.mvvmfx.FluentViewLoader;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.animation.Timeline;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.input.DataFormat;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

public class ChatContainerView implements FxmlView<ChatContainerViewModel>, Initializable {

    @FXML
    private BorderPane container;

    private GridPane chatContainer;

    @InjectViewModel
    private ChatContainerViewModel viewModel;

    private Map<ChatViewModel, Node> items;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        items = new HashMap<>();
        viewModel.getChatList().addListener(this::chatChanged);

        chatContainer = new GridPane();
        container.setCenter(chatContainer);
    }

    private void chatChanged(ListChangeListener.Change<? extends ChatViewModel> c) {
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

    private void onRemoved(ChatViewModel chat) {
        var node = items.get(chat);
        if (node == null) return;

        chatContainer.getChildren().remove(node);
    }

    private void onAdded(ChatViewModel chat) {
        var tuple = FluentViewLoader.fxmlView(ChatView.class)
                .viewModel(chat)
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

    private static class DragController {
        private static final DataFormat MOVE_DATA = new DataFormat("application/chat-view");

        private Timeline dragOverAnimation;

        public void installDragEvents(Node node) {
            // ドラッグの開始
            node.setOnDragDetected(e -> {

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
