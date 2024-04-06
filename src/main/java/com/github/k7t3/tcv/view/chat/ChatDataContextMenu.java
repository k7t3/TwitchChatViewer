package com.github.k7t3.tcv.view.chat;

import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.chat.filter.UserChatMessageFilter;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.app.core.Resources;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextInputDialog;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.StackedFontIcon;

@SuppressWarnings("ALL")
public class ChatDataContextMenu extends ContextMenu {

    private final ChatDataViewModel viewModel;

    public ChatDataContextMenu(ChatDataViewModel viewModel) {
        this.viewModel = viewModel;

        setHideOnEscape(true);

        if (viewModel.isSystem())
            return;

        var prefs = AppPreferences.getInstance().getMessageFilterPreferences();

        // チャットメッセージをコピー
        var copy = new MenuItem(Resources.getString("chat.menu.copy.message"));
        copy.setOnAction(e -> viewModel.copyMessage());

        // ユーザーのチャットを非表示にする
        var hideMessageAsUser = new MenuItem(
                Resources.getString("chat.menu.filter.user"),
                blockIcon(Feather.USER)
        );
        hideMessageAsUser.setOnAction(e -> {
            addHiddenUser();
        });

        // チャットメッセージを非表示にする
        var hideMessageAsRegex = new MenuItem(
                Resources.getString("chat.menu.filter.regex"),
                blockIcon(Feather.MESSAGE_CIRCLE)
        );
        hideMessageAsRegex.setOnAction(e -> {
            addHiddenRegexMessage();
        });

        getItems().addAll(copy, new SeparatorMenuItem(), hideMessageAsUser, hideMessageAsRegex);
    }

    private Node blockIcon(Ikon icon) {
        var stack = new StackedFontIcon();
        stack.setIconCodes(icon, Feather.SLASH);
        return stack;
    }

    private void addHiddenRegexMessage() {
        var prefs = AppPreferences.getInstance().getMessageFilterPreferences();

        var message = viewModel.getMessage().getPlain();
        var filter = prefs.getRegexChatMessageFilter();
        filter.getRegexes().add(message);

        // 非表示
        viewModel.setHidden(true);
    }

    private void addHiddenUser() {
        var dialog = new TextInputDialog("");
        dialog.setTitle("Hide User");
        dialog.setHeaderText(
                Resources.getString("chat.filter.user.dialog.header.format")
                        .formatted(viewModel.getUserName())
        );
        dialog.setContentText("Comment");
        dialog.initOwner(getOwnerWindow());
        dialog.showAndWait().ifPresent(comment -> {
            var c = comment == null || comment.trim().isEmpty()
                    ? viewModel.getUserName()
                    : comment.trim();
            var chat = viewModel.getChatData();
            var prefs = AppPreferences.getInstance().getMessageFilterPreferences();
            var user = new UserChatMessageFilter.FilteredUser(chat.userId(), chat.userName(), c);
            prefs.getUserChatMessageFilter().getUsers().add(user);

            // 非表示
            viewModel.setHidden(true);
        });
    }

}
