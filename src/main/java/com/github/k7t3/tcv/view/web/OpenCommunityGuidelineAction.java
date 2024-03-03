package com.github.k7t3.tcv.view.web;

import com.github.k7t3.tcv.view.action.Action;

public class OpenCommunityGuidelineAction implements Action {

    private static final String GUIDELINE_URL = "https://safety.twitch.tv/s/article/Community-Guidelines";

    private final BrowserController controller;

    public OpenCommunityGuidelineAction(BrowserController controller) {
        this.controller = controller;
    }

    @Override
    public void run() {
        controller.load(GUIDELINE_URL);
        controller.show();
    }

}
