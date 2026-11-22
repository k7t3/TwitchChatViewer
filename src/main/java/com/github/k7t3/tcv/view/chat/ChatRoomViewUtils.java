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

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomViewModel;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import com.github.k7t3.tcv.view.channel.LiveInfoPopup;
import com.github.k7t3.tcv.view.core.FloatableStage;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import com.github.k7t3.tcv.view.core.ViewResources;
import com.github.k7t3.tcv.view.core.WindowBoundsScope;
import java.util.concurrent.atomic.AtomicBoolean;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.StackPane;
import javafx.util.Subscription;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;


public class ChatRoomViewUtils {

    private ChatRoomViewUtils() {
    }

    /**
     * オフライン時だけ背景を表示し、遅れて取得した画像にも追従する。
     * 初めてオフラインになるまで画像を要求しない。FX スレッドから利用する。
     * @param channel 表示するチャンネル
     * @return 配信状態と画像の変更を反映する背景。利用終了時に dispose できる
     */
    public static ObjectBinding<Background> offlineBackground(TwitchChannelViewModel channel) {
        return new ObjectBinding<>() {
            private ObservableValue<Image> image;
            {
                bind(channel.liveProperty());
            }

            @Override
            protected Background computeValue() {
                if (channel.isLive()) return null;
                if (image == null) {
                    image = channel.offlineImageProperty();
                    bind(image);
                }
                var current = image.getValue();
                if (current == null) return null;
                var size = new BackgroundSize(BackgroundSize.AUTO, BackgroundSize.AUTO, true, true, true, false);
                return new Background(new BackgroundImage(current, BackgroundRepeat.REPEAT, BackgroundRepeat.REPEAT,
                        BackgroundPosition.DEFAULT, size));
            }

            @Override
            public void dispose() {
                unbind(channel.liveProperty());
                if (image != null) unbind(image);
            }
        };
    }

    public static Subscription initializeVirtualFlowScrollActions(
            VirtualFlow<?, ?> virtualFlow,
            ObservableList<?> items,
            BooleanProperty autoScroll
    ) {
        var active = new AtomicBoolean(true);
        EventHandler<ScrollEvent> scrolling = e -> {
            if (!active.get()) return;
            // 上方向にスクロールしたときはオートスクロールを無効化
            if (0 < e.getDeltaY()) {
                autoScroll.set(false);
            }

            // 最後までスクロールしたときはオートスクロールを有効化
            else if (e.getDeltaY() < 0 &&
                    items.size() - 1 == virtualFlow.getLastVisibleIndex()) {
                autoScroll.set(true);
            }
        };

        // 自動スクロールの設定
        ListChangeListener<Object> changed = c -> {
            if (active.get() && autoScroll.get() && c.next() && c.wasAdded()) {
                virtualFlow.showAsLast(c.getList().size() - 1);
            }
        };
        virtualFlow.addEventHandler(ScrollEvent.SCROLL, scrolling);
        items.addListener(changed);
        return () -> {
            if (!active.getAndSet(false)) return;
            CloseActions.runAll(() -> virtualFlow.removeEventHandler(ScrollEvent.SCROLL, scrolling),
                    () -> items.removeListener(changed));
        };
    }

    /** メッセージ表示とスクロール監視を一つの画面資源として所有する。 */
    public static Subscription installMessages(StackPane container, ChatRoomViewModel room, boolean merged) {
        var owned = new ViewResources();
        try {
            var flow = VirtualFlow.createVertical(room.getChatDataList(), merged ? ChatDataCell::merged : ChatDataCell::of);
            owned.add(flow::dispose);
            var scrollPane = new VirtualizedScrollPane<>(flow);
            owned.add(() -> container.getChildren().remove(scrollPane));
            container.getChildren().add(scrollPane);
            var scrolling = initializeVirtualFlowScrollActions(flow, room.getChatDataList(), room.autoScrollProperty());
            owned.add(scrolling::unsubscribe);
            return owned::close;
        } catch (RuntimeException | Error failure) {
            CloseActions.runAll(() -> { if (failure instanceof RuntimeException exception) throw exception; throw (Error) failure; }, owned::close);
            throw failure;
        }
    }

    public static WindowBoundsScope initializeFloatableStage(FloatableStage stage, ChatRoomViewModel chatRoom,
                                                             WindowBoundsService service) {
        return new WindowBoundsScope(stage, chatRoom.getIdentity(), service);
    }

    public static Subscription installStreamInfoPopOver(TwitchChannelViewModel channel, Node node) {
        var popup = new LiveInfoPopup(channel);
        popup.setAutoHide(true);
        var active = new AtomicBoolean(true);
        EventHandler<MouseEvent> moved = e -> {
            if (!active.get() || !channel.isLive())
                return;

            var bounds = JavaFXHelper.computeScreenBounds(node);
            if (bounds == null) return;
            var x = bounds.getMinX() - popup.getWidth() / 2 + bounds.getWidth() / 2;
            var y = bounds.getMaxY();
            popup.show(node, x, y);
        };
        EventHandler<MouseEvent> exited = e -> popup.hide();
        node.addEventHandler(MouseEvent.MOUSE_MOVED, moved);
        node.addEventHandler(MouseEvent.MOUSE_EXITED, exited);
        return () -> {
            if (!active.getAndSet(false)) return;
            CloseActions.runAll(() -> node.removeEventHandler(MouseEvent.MOUSE_MOVED, moved),
                    () -> node.removeEventHandler(MouseEvent.MOUSE_EXITED, exited), popup::close);
        };
    }


}
