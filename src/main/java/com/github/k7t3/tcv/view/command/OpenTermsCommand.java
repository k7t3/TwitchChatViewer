/*
 * Copyright 2024 k7t3
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
