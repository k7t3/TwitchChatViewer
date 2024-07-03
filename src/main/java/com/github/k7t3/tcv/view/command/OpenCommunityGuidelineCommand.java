package com.github.k7t3.tcv.view.command;

import com.github.k7t3.tcv.app.core.DesktopUtils;
import com.github.k7t3.tcv.app.service.BasicCommand;
import com.github.k7t3.tcv.app.service.FXTask;

public class OpenCommunityGuidelineCommand extends BasicCommand {

    private static final String GUIDELINE_URL = "https://safety.twitch.tv/s/article/Community-Guidelines";

    public OpenCommunityGuidelineCommand() {
        executable.set(true);
    }

    @Override
    public void execute() {
        FXTask.task(() -> {
            DesktopUtils.browse(GUIDELINE_URL);
            return null;
        }).runAsync();
    }
}
