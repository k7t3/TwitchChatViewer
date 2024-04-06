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
