package com.github.k7t3.tcv.view.web;

import com.github.k7t3.tcv.view.action.Action;

public class OpenTermsAction implements Action {

    private static final String TERMS_URL = "https://www.twitch.tv/p/legal/terms-of-service/";

    private final BrowserController controller;

    public OpenTermsAction(BrowserController controller) {
        this.controller = controller;
    }

    @Override
    public void run() {
        controller.load(TERMS_URL);
        controller.show();
    }

}
