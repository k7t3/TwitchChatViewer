package com.github.k7t3.tcv.app.help;

import com.github.k7t3.tcv.app.model.AppViewModel;
import com.github.k7t3.tcv.app.core.DesktopUtils;
import com.github.k7t3.tcv.app.service.FXTask;
import com.github.k7t3.tcv.domain.event.EventSubscribers;

public class AboutViewModel implements AppViewModel {

    private static final String GITHUB_LINK = "https://github.com/k7t3/TwitchChatViewer";
    private static final String LICENSE_LINK = "https://github.com/k7t3/TwitchChatViewer/blob/main/docs/license.md";
    private static final String LIBRARIES_LINK = "https://github.com/k7t3/TwitchChatViewer/blob/main/docs/external-libraries.md";

    public AboutViewModel() {
    }

    private void browse(String link) {
        FXTask.task(() -> {
            DesktopUtils.browse(link);
            return null;
        }).runAsync();
    }

    public void browseGitHubPage() {
        browse(GITHUB_LINK);
    }

    public void browseLicenseWithSecurityPolicyPage() {
        browse(LICENSE_LINK);
    }

    public void browseLibrariesPage() {
        browse(LIBRARIES_LINK);
    }

    @Override
    public void subscribeEvents(EventSubscribers eventSubscribers) {
        // no-op
    }

    @Override
    public void onLogout() {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }
}
