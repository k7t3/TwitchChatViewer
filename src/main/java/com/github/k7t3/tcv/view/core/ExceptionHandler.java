package com.github.k7t3.tcv.view.core;

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
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Exception Occurred");
        alert.setContentText("Exception Occurred: " + e.getMessage());
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
