package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.app.channel.ChannelViewModelRepository;
import com.github.k7t3.tcv.app.channel.TwitchChannelListViewModel;
import com.github.k7t3.tcv.app.chat.ChatResourcesLoader;
import com.github.k7t3.tcv.app.chat.ChatRoomContainerViewModel;
import com.github.k7t3.tcv.app.clip.PostedClipRepository;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.OS;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.image.ImageLoader;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.model.AbstractViewModel;
import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.app.service.LiveStateNotificator;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.k7t3.tcv.lifecycle.CloseActions;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** 起動単位の共有サービスからMainのモデルを構成し、途中失敗の資源も回収する。 */
public final class MainModelFactory {
    private MainModelFactory() {}

    /** 標準の共有サービスと認証処理でMainを構築する。返したモデルが子を所有する。 */
    public static MainViewModel create(AppHelper helper, Consumer<Throwable> failures) {
        return create(helper, helper.getChannelGroupRepository(), helper.getImageLoader(), failures);
    }

    /** 借用するグループ・画像を明示して、標準の認証処理でMainを構築する。 */
    public static MainViewModel create(AppHelper helper, ChannelGroupRepository groups, ImageLoader images,
                                       Consumer<Throwable> failures) {
        return create(helper, groups, images, failures, store -> new AuthenticatorViewModel(store,
                helper.getClientSessions()::adopt, helper.getExecutor(), failures));
    }

    /** 認証の生成先を指定する。生成関数は未開始のモデルを返し、開始は画面の所有後に行う。 */
    public static MainViewModel create(AppHelper helper, Consumer<Throwable> failures,
                                       Function<CredentialStore, AuthenticatorViewModel> authentication) {
        return create(helper, helper.getChannelGroupRepository(), helper.getImageLoader(), failures, authentication);
    }

    private static MainViewModel create(AppHelper helper, ChannelGroupRepository groups, ImageLoader images,
                                        Consumer<Throwable> failures, Function<CredentialStore, AuthenticatorViewModel> authentication) {
        Objects.requireNonNull(failures);
        Objects.requireNonNull(authentication);
        var owned = new ArrayList<AbstractViewModel>();
        try {
            var executor = helper.getExecutor();
            var source = helper.getSubscribers();
            var sessions = helper.getClientSessions();
            var channels = own(owned, new ChannelViewModelRepository(groups, images, executor, helper::awaitOnShutdown, failures));
            var chats = own(owned, createChat(helper, images));
            var list = own(owned, new TwitchChannelListViewModel(new BrowserActions(executor, helper::awaitOnShutdown, failures)));
            var clips = own(owned, new PostedClipRepository(images, executor, helper::awaitOnShutdown, failures));
            var notifications = own(owned, new LiveStateNotificator(channels, failures));
            return new MainViewModel(sessions, source, channels, chats, list, clips, notifications, store -> {
                var model = authentication.apply(store);
                try {
                    helper.awaitOnShutdown(model.resourcesReleased());
                    model.setEventSubscribers(source);
                    model.delegateLifecycleToOwner();
                    return model;
                } catch (RuntimeException | Error failure) {
                    try { model.close(); }
                    catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                    throw failure;
                }
            }, failures);
        } catch (RuntimeException | Error failure) {
            try { CloseActions.runAll(owned.reversed().stream().map(model -> (Runnable) model::close).toArray(Runnable[]::new)); }
            catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    /** 読込開始時のFXスレッドで現在のセッションとフィルターを固定する。 */
    public static ChatRoomContainerViewModel createChat(AppHelper helper, ImageLoader images) {
        return new ChatRoomContainerViewModel(() -> {
            var filters = helper.getChatFilters();
            var loader = new ChatResourcesLoader(OS.current().getApplicationDirectory(),
                    Objects.requireNonNull(helper.getTwitch()), filters::readSnapshot, images);
            return new ChatRoomContainerViewModel.LoadRequest(filters, loader::load);
        }, helper.getExecutor(), helper::awaitOnShutdown);
    }

    private static <T extends AbstractViewModel> T own(List<AbstractViewModel> owner, T model) {
        owner.add(model);
        return model;
    }
}
