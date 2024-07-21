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

import com.github.k7t3.tcv.app.chat.ChatDataViewModel;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.chat.filter.UserFilterEntry;
import com.github.k7t3.tcv.app.core.AppHelper;
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
        viewModel.keywordFilter();
    }

    private void addHiddenUser() {
        var dialog = new TextInputDialog("");
        dialog.setTitle(Resources.getString("alert.title.confirmation"));
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
            viewModel.userFilter(c);
        });
    }

}
