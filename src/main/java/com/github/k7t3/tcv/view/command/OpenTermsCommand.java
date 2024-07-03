package com.github.k7t3.tcv.view.command;

import com.github.k7t3.tcv.app.core.DesktopUtils;
import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.app.service.FXTask;

public class OpenTermsCommand extends BasicCommand {

    private static final String TERMS_URL = "https://www.twitch.tv/p/legal/terms-of-service/";

    public OpenTermsCommand() {
        executable.set(true);
    }

    @Override
    public void execute() {
        FXTask.task(() -> {
            DesktopUtils.browse(TERMS_URL);
            return null;
        }).runAsync();
    }
}
