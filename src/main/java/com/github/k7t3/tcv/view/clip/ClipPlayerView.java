package com.github.k7t3.tcv.view.clip;

import atlantafx.base.controls.ToggleSwitch;
import atlantafx.base.theme.Styles;
import com.github.k7t3.tcv.app.clip.ClipPlayerViewModel;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.prefs.PlayerPreferences;
import de.saxsys.mvvmfx.FxmlView;
import de.saxsys.mvvmfx.InjectViewModel;
import javafx.animation.FadeTransition;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

public class ClipPlayerView implements FxmlView<ClipPlayerViewModel>, Initializable {

    @FXML
    private AnchorPane root;

    @FXML
    private Label titleLabel;

    @FXML
    private ToggleSwitch autoSwitch;

    @FXML
    private Pane headerPane;

    @FXML
    private StackPane content;

    @FXML
    private MediaView mediaView;

    @FXML
    private ProgressIndicator loadingMask;

    @FXML
    private Pane controllerPane;

    @FXML
    private ToggleButton playPauseToggleButton;

    @FXML
    private Slider seekSlider;

    @FXML
    private Label currentLabel;

    @FXML
    private Label endLabel;

    @FXML
    private ToggleButton volumeToggleButton;

    @FXML
    private Slider volumeSlider;

    @InjectViewModel
    private ClipPlayerViewModel viewModel;

    private PlayerPreferences prefs;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        prefs = AppPreferences.getInstance().getPlayerPreferences();

        titleLabel.getStyleClass().addAll(Styles.TITLE_3);
        autoSwitch.selectedProperty().bindBidirectional(prefs.autoProperty());

        initHeader();

        mediaView.fitWidthProperty().bind(root.widthProperty());
        mediaView.fitHeightProperty().bind(root.heightProperty());

        loadingMask.prefWidthProperty().bind(root.widthProperty().multiply(0.5));
        loadingMask.prefHeightProperty().bind(root.heightProperty().multiply(0.5));
        loadingMask.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        // 初期状態を更新
        loadingMask.setVisible(true);
        controllerPane.setVisible(false);

        playPauseToggleButton.getStyleClass().addAll(Styles.BUTTON_ICON);
        volumeToggleButton.getStyleClass().addAll(Styles.BUTTON_ICON);

        volumeSlider.visibleProperty().bind(volumeToggleButton.selectedProperty().not());

        initFooter();

        bindViewModels();
    }

    private void initHeader() {
        var stops = new Stop[] {
                new Stop(0, Color.rgb(0, 0, 0, 0.8)),
                new Stop(1, Color.TRANSPARENT)
        };
        var linear = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, stops);
        var fill = new BackgroundFill(linear, CornerRadii.EMPTY, Insets.EMPTY);
        var bg = new Background(fill);
        headerPane.setBackground(bg);

        var fadeController = new FadeController(headerPane);
        headerPane.setOnMouseEntered(e -> fadeController.fadeIn());
        headerPane.setOnMouseExited(e -> fadeController.fadeOut());
        fadeController.fadeOut();
    }

    private void initFooter() {
        var stops = new Stop[] {
                new Stop(0, Color.TRANSPARENT),
                new Stop(1, Color.rgb(0, 0, 0, 0.8))
        };
        var linear = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, stops);
        var fill = new BackgroundFill(linear, CornerRadii.EMPTY, Insets.EMPTY);
        var bg = new Background(fill);
        controllerPane.setBackground(bg);

        var fadeController = new FadeController(controllerPane);
        controllerPane.setOnMouseEntered(e -> fadeController.fadeIn());
        controllerPane.setOnMouseExited(e -> fadeController.fadeOut());
        fadeController.fadeOut();
    }

    private void bindViewModels() {
        titleLabel.textProperty().bind(viewModel.getClip().titleProperty());
        loadingMask.visibleProperty().bind(viewModel.loadedProperty().not());
        controllerPane.visibleProperty().bind(viewModel.loadedProperty());

        viewModel.clipProperty().addListener((ob, o, n) -> {
            if (n != null) initPlayerControls();
        });

        initPlayerControls();
    }

    private boolean seekChanging = false;

    private void initPlayerControls() {
        var media = viewModel.getMedia();
        var player = viewModel.getPlayer();
        mediaView.setMediaPlayer(player);

        // 再生時間に関するラベル
        currentLabel.textProperty().bind(player.currentTimeProperty().map(this::durationToString));
        endLabel.textProperty().bind(media.durationProperty().map(this::durationToString));

        // メディアが最後まで到達したら再生ボタンの状態を戻す
        player.setOnEndOfMedia(() -> playPauseToggleButton.setSelected(false));

        // 再生ボタンを押したときの動作
        playPauseToggleButton.setOnAction(e -> {
            if (playPauseToggleButton.isSelected()) {
                viewModel.play();
            } else
                viewModel.pause();
        });

        // ミュート状態
        player.muteProperty().bindBidirectional(prefs.mutedProperty());
        volumeToggleButton.selectedProperty().bindBidirectional(player.muteProperty());

        // 音量スライダ
        player.volumeProperty().bindBidirectional(prefs.volumeProperty());
        volumeSlider.valueProperty().bindBidirectional(player.volumeProperty());


        // シークスライダの範囲を定義
        seekSlider.setMin(0);
        seekSlider.maxProperty().bind(media.durationProperty().map(Duration::toMillis));

        // プレイヤーの時間に合わせてシークを変動
        player.currentTimeProperty().addListener((ob, o, n) -> seekSlider.setValue(n.toMillis()));

        // シークがユーザーに変更されたときにプレイヤーに反映
        seekSlider.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> seekChanging = true);
        seekSlider.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> seekChanging = false);
        seekSlider.valueProperty().addListener((ob, o, n) -> {
            if (seekChanging) {
                player.seek(Duration.millis(n.doubleValue()));
            }
        });

        if (prefs.isAuto()) {
            viewModel.play();
            playPauseToggleButton.setSelected(true);
        }
    }

    private String durationToString(Duration duration) {
        var totalSeconds = (int) duration.toSeconds();

        if (3600 < totalSeconds) {
            var hours = totalSeconds / 3600;
            var minutes = (totalSeconds - 3600 * hours) / 60;
            var seconds = totalSeconds - 3600 * hours - 60 * minutes;
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }

        if (60 < totalSeconds) {
            var minutes = totalSeconds / 60;
            var seconds = totalSeconds - 60 * minutes;
            return String.format("00:%02d:%02d", minutes, seconds);
        }

        return String.format("00:00:%02d", totalSeconds);
    }

    public MediaView getMediaView() {
        return mediaView;
    }

    private static class FadeController {

        private final Node node;

        private FadeTransition fade;

        public FadeController(Node node) {
            this.node = node;
        }

        public void fadeIn() {
            if (fade != null) fade.stop();
            fade = new FadeTransition(Duration.millis(300), node);
            fade.setFromValue(node.getOpacity());
            fade.setToValue(1.0);
            fade.play();
        }

        public void fadeOut() {
            if (fade != null) fade.stop();
            fade = new FadeTransition(Duration.millis(300), node);
            fade.setFromValue(node.getOpacity());
            fade.setToValue(0.1);
            fade.play();
        }

    }

}
