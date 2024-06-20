package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.chat.ChatRoomViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.view.channel.LiveInfoPopup;
import com.github.k7t3.tcv.view.core.FloatableStage;
import com.github.k7t3.tcv.view.core.JavaFXHelper;
import com.github.k7t3.tcv.view.core.WindowBoundsListener;
import javafx.beans.property.BooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import org.fxmisc.flowless.VirtualFlow;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ChatRoomViewUtils {

    private ChatRoomViewUtils() {
    }

    public static void initializeVirtualFlowScrollActions(
            VirtualFlow<?, ?> virtualFlow,
            ObservableList<?> items,
            BooleanProperty autoScroll
    ) {
        virtualFlow.addEventHandler(ScrollEvent.SCROLL, e -> {
            // 上方向にスクロールしたときはオートスクロールを無効化
            if (0 < e.getDeltaY()) {
                autoScroll.set(false);
            }

            // 最後までスクロールしたときはオートスクロールを有効化
            else if (e.getDeltaY() < 0 &&
                    items.size() - 1 == virtualFlow.getLastVisibleIndex()) {
                autoScroll.set(true);
            }
        });

        // 自動スクロールの設定
        items.addListener((ListChangeListener<? super Object>) c -> {
            if (autoScroll.get() && c.next() && c.wasAdded()) {
                virtualFlow.showAsLast(c.getList().size() - 1);
            }
        });
    }

    private static String computeHash(String identity) {
        try {
            var digest = MessageDigest.getInstance("md5");
            var bytes = identity.getBytes(StandardCharsets.UTF_8);
            bytes = digest.digest(bytes);
            return new String(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static void initializeFloatableStage(FloatableStage stage, ChatRoomViewModel chatRoom) {
        // ウインドウの座標設定を取り出す
        var helper = AppHelper.getInstance();

        // チャットにおける識別子を使用して設定を取り出す
        var identity = computeHash(chatRoom.getIdentity());

        var windowService = helper.getWindowBoundsService();

        // 保存されている座標を割り当て
        windowService.getBoundsAsync(identity)
                .onDone(bounds -> bounds.apply(stage));

        // 座標の追跡設定
        var listener = new WindowBoundsListener();
        listener.install(stage);

        // ウインドウを閉じるときに座標を記録
        stage.setOnHiding(e -> {
            var current = listener.getCurrent();
            windowService.saveBoundsAsync(identity, current);
        });
    }

    public static void installStreamInfoPopOver(TwitchChannelViewModel channel, Node node) {
        var popup = new LiveInfoPopup(channel);
        popup.setAutoHide(true);
        node.addEventHandler(MouseEvent.MOUSE_MOVED, e -> {
            var bounds = JavaFXHelper.computeScreenBounds(node);
            var x = bounds.getMinX() - popup.getWidth() / 2 + bounds.getWidth() / 2;
            var y = bounds.getMaxY();
            popup.show(node, x, y);
        });
        node.addEventHandler(MouseEvent.MOUSE_EXITED, e -> popup.hide());
    }


}
