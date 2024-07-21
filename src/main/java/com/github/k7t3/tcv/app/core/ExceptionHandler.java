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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionHandler.class);

    private ExceptionHandler() {
    }

    public static void handle(Throwable e) {
        handle(null, e);
    }

    public static void handle(Stage parent, Throwable e) {
        LOGGER.error(e.getMessage(), e);

        // 資格情報が無効になったとき
        if (e instanceof InvalidCredentialException) {

            var helper = AppHelper.getInstance();
            var primaryStage = helper.getPrimaryStage();

            var alert = new Alert(Alert.AlertType.ERROR);
            alert.initOwner(primaryStage);
            alert.setTitle("Twitch API Error");
            alert.setHeaderText(Resources.getString("terminate.header"));
            alert.setContentText(Resources.getString("terminate.content"));
            alert.showAndWait();

            primaryStage.close();

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

        alert.showAndWait();
    }

}
