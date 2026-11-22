package com.github.k7t3.tcv.view;

import atlantafx.base.controls.ModalPane;
import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.SearchChannelViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.ExceptionHandler;
import com.github.k7t3.tcv.app.help.AboutViewModel;
import com.github.k7t3.tcv.app.keyboard.KeyBinding;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.prefs.*;
import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.app.theme.ThemeManager;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.view.channel.TwitchChannelListView;
import com.github.k7t3.tcv.view.chat.ChatContainerView;
import com.github.k7t3.tcv.view.command.*;
import com.github.k7t3.tcv.view.help.AboutView;
import com.github.k7t3.tcv.view.main.MainView;
import com.github.k7t3.tcv.view.prefs.PreferencesView;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** 起動側でViewとコマンドの依存を接続する。返した部品の所有は画面へ渡す。 */
public final class MainViewFactory {
    private MainViewFactory() {}

    /** 共有サービスの取得と子の生成は、以前と同じFX上の利用時点まで遅延させる。 */
    public static MainView create(Stage owner, MainViewModel model, AppHelper helper, AppPreferences preferences,
                                   ThemeManager theme, CredentialStore credentials) {
        return new MainView(preferences, credentials, new MainView.Components(
                helper::getKeyBindingCombinations,
                pane -> new OpenChannelGroupCommand(pane, helper.getChannelGroupRepository(), preferences, model.authorizedProperty()),
                pane -> new OpenClipCommand(pane, model.getClipRepository(), model.authorizedProperty().and(model.clipCountProperty().greaterThan(0))),
                () -> preferences(helper, preferences, stage -> new PreferencesView(preferencesPages(helper, preferences, theme, stage)), Stage::new),
                pane -> search(pane, model.getChannelRepository(), helper, model.authorizedProperty()),
                () -> about(helper, AboutView::new, Stage::new),
                () -> new BrowserActions(helper.getExecutor(), helper::awaitOnShutdown, failure -> ExceptionHandler.handle(owner, failure)),
                () -> new TwitchChannelListView(helper::getChannelGroupRepository, preferences, () -> owner),
                () -> new ChatContainerView(helper.getChannelGroupRepository(), preferences.getChatPreferences(), helper::getWindowBoundsService)));
    }

    /** Aboutの子Stageを通知先にしたモデルを、そのStageの生成後に構築する。 */
    public static OpenAboutCommand about(AppHelper helper, Supplier<AboutView> views, Supplier<Stage> stages) {
        return new OpenAboutCommand(helper::getPrimaryStage, stage -> new AboutViewModel(new BrowserActions(
                helper.getExecutor(), helper::awaitOnShutdown, failure -> ExceptionHandler.handle(stage, failure))), views, stages);
    }

    /** 検索モデルの生成と現在セッションへの接続を、モーダルの所有処理へ渡す。 */
    public static OpenSearchChannelCommand search(ModalPane pane, ChannelViewModelRepository repository, AppHelper helper,
                                                  ObservableValue<Boolean> condition) {
        return search(pane, helper, condition, () -> new SearchChannelViewModel(repository, helper.getImageLoader(),
                helper.getExecutor(), failure -> ExceptionHandler.handle(helper.getPrimaryStage(), failure)));
    }

    /** 検索のセッション接続は、コマンドがモデルとViewを所有した後に行う。 */
    public static OpenSearchChannelCommand search(ModalPane pane, AppHelper helper, ObservableValue<Boolean> condition,
                                                  Supplier<SearchChannelViewModel> models) {
        return new OpenSearchChannelCommand(pane, condition, models, model -> {
            model.setEventSubscribers(helper.getSubscribers());
            model.twitchProperty().bind(helper.twitchProperty());
        });
    }

    /** 設定Stageを先に所有してから、同じStageへ通知するモデルとViewを構成する。 */
    public static OpenPreferencesCommand preferences(AppHelper helper, AppPreferences preferences,
                                                     Function<Stage, PreferencesView> views, Supplier<Stage> stages) {
        return new OpenPreferencesCommand(helper::getPrimaryStage, stage -> new PreferencesViewModel(
                new SettingsSaveService(preferences, helper.getChatFilterStore()), helper.getExecutor(),
                helper::awaitOnShutdown,
                failure -> ExceptionHandler.handle(stage, failure)), views, stages);
    }

    /** ページはFXML初期化中に順に生成し、設定Viewがそれぞれを所有する。 */
    public static PreferencesView.Pages preferencesPages(AppHelper helper, AppPreferences preferences, ThemeManager theme, Stage owner) {
        return new PreferencesView.Pages(
                () -> new GeneralPreferencesViewModel(preferences.getGeneralPreferences(), theme),
                () -> new ChatPreferencesViewModel(preferences.getChatPreferences(), helper.getExecutor(),
                        failure -> ExceptionHandler.handle(owner, failure)),
                () -> new KeywordFilterViewModel(helper.getChatFilters()),
                () -> new UserFilterViewModel(helper.getChatFilters()),
                () -> new KeyBindingPreferencesViewModel(helper.getKeyBindingCombinations()),
                () -> prepareCommit(helper, preferences, theme));
    }

    private static Consumer<SettingsSaveService.SavedSettings> prepareCommit(AppHelper helper, AppPreferences preferences, ThemeManager theme) {
        // 保存受付時の対象を固定し、画面終了後の成功でも共有サービスを再取得しない。
        var applyFilters = helper.getChatFilters().captureCommitHandler();
        var keys = helper.getKeyBindingCombinations();
        return saved -> {
            var current = preferences.applyCommittedValues(saved.draft().values());
            for (var binding : KeyBinding.values()) {
                var value = current.get(binding.getId());
                if (value != null) keys.updateCombination(binding, value.map(KeyCombination::keyCombination)
                        .orElseGet(binding::getDefaultCombination));
            }
            theme.setTheme(preferences.getGeneralPreferences().getTheme());
            theme.setThemeType(preferences.getGeneralPreferences().getThemeType());
            applyFilters.accept(saved.filters());
        };
    }
}
