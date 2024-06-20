package com.github.k7t3.tcv.view.command;

import com.github.k7t3.tcv.app.command.BasicCommand;
import com.github.k7t3.tcv.view.web.BrowserController;

public class OpenCommunityGuidelineCommand extends BasicCommand {

    private static final String GUIDELINE_URL = "https://safety.twitch.tv/s/article/Community-Guidelines";

    private final BrowserController controller;

    public OpenCommunityGuidelineCommand(BrowserController controller) {
        this.controller = controller;
        executable.set(true);
    }

    @Override
    public void execute() {
        controller.load(GUIDELINE_URL);
        controller.show();
    }
}
