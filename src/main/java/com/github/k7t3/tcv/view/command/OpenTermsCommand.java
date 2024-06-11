package com.github.k7t3.tcv.view.command;

import com.github.k7t3.tcv.app.command.BasicCommand;
import com.github.k7t3.tcv.view.web.BrowserController;

public class OpenTermsCommand extends BasicCommand {

    private static final String TERMS_URL = "https://www.twitch.tv/p/legal/terms-of-service/";

    private final BrowserController controller;

    public OpenTermsCommand(BrowserController controller) {
        this.controller = controller;
    }

    @Override
    public void execute() {
        controller.load(TERMS_URL);
        controller.show();
    }
}
