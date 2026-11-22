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

package com.github.k7t3.tcv.app.core;

import com.github.k7t3.tcv.domain.exception.InvalidCredentialException;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandler.class);

    private ExceptionHandler() {
    }

    /**
     * 指定された所有者へ表示する。認証失効では、所有関係の最上位Stageを終了する。
     * 所有者がなければ、別アプリのウインドウを取得・終了しない。
     */
    public static void handle(Stage parent, Throwable e) {
        LOGGER.error(e.getMessage(), e);

        // 資格情報が無効になったとき
        if (e instanceof InvalidCredentialException) {

            var application = parent;
            while (application != null && application.getOwner() instanceof Stage owner) application = owner;
            var alert = new Alert(Alert.AlertType.ERROR);
            if (application != null) alert.initOwner(application);
            alert.setTitle("Twitch API Error");
            alert.setHeaderText(Resources.getString("terminate.header"));
            alert.setContentText(Resources.getString("terminate.content"));
            showOwned(application, alert);

            if (application != null) application.close();

            return;

        }

        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Exception Occurred");
        alert.setHeaderText("Exception Occurred: " + e.getMessage());
        if (parent != null) alert.initOwner(parent);

        var stacktrace = new TextArea();
        stacktrace.setEditable(false);

        try (var sw = new StringWriter();var pw = new PrintWriter(sw)) {
            e.printStackTrace(pw);
            pw.flush();
            stacktrace.setText(sw.toString());
        } catch (IOException ex) {
            LOGGER.error(ex.getMessage(), ex);
        }

        var pane = alert.getDialogPane();
        pane.setContent(stacktrace);
        pane.setExpanded(false);

        showOwned(parent, alert);
    }

    private static void showOwned(Stage owner, Alert alert) {
        if (owner == null) {
            alert.showAndWait();
            return;
        }
        EventHandler<WindowEvent> closed = event -> alert.close();
        owner.addEventHandler(WindowEvent.WINDOW_HIDDEN, closed);
        try {
            alert.showAndWait();
        } finally {
            owner.removeEventHandler(WindowEvent.WINDOW_HIDDEN, closed);
        }
    }

}
