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

package com.github.k7t3.tcv.view.channel;

import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.service.LiveStateNotificator;
import com.github.k7t3.tcv.view.core.ToStringConverter;
import de.saxsys.mvvmfx.InjectViewModel;
import de.saxsys.mvvmfx.JavaView;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.util.StringConverter;

import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.ResourceBundle;

public class LiveStateNotificatorView extends ListView<LiveStateNotificator.LiveStateRecord> implements JavaView<LiveStateNotificator>, Initializable {

    @InjectViewModel
    private LiveStateNotificator notificator;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

    private final StringConverter<LiveStateNotificator.LiveStateRecord> converter = new ToStringConverter<>(record -> {
        var name = record.channel().getUserName();
        var time = record.time().format(formatter);
        if (record.live()) {
            return Resources.getString("main.live.state.online").formatted(name, time);
        } else {
            return Resources.getString("main.live.state.offline").formatted(name, time);
        }
    });

    public LiveStateNotificatorView() {
        setCellFactory(p -> new Cell(converter));
        getStyleClass().addAll(Styles.DENSE, Tweaks.EDGE_TO_EDGE);
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
    }

    public StringConverter<LiveStateNotificator.LiveStateRecord> getConverter() {
        return converter;
    }

    private class Cell extends TextFieldListCell<LiveStateNotificator.LiveStateRecord> {

        private Button openButton;

        public Cell(StringConverter<LiveStateNotificator.LiveStateRecord> converter) {
            super(converter);
            setContentDisplay(ContentDisplay.RIGHT);
        }

        private Button getOpenButton() {
            if (openButton == null) {
                var b = new Button("Open");
                b.getStyleClass().addAll(Styles.SMALL, Styles.ROUNDED);
                b.setOnAction(e -> {
                    var item = getItem();
                    if (item == null) return;

                    var channel = item.channel();
                    notificator.publish(new ChatOpeningEvent(channel));
                });
                openButton = b;
            }
            openButton.disableProperty().bind(getItem().channel().liveProperty().not());
            return openButton;
        }

        @Override
        public void updateItem(LiveStateNotificator.LiveStateRecord record, boolean b) {
            super.updateItem(record, b);
            if (record != null && record.live()) {
                setGraphic(getOpenButton());
            } else {
                setGraphic(null);
            }
        }
    }
}
