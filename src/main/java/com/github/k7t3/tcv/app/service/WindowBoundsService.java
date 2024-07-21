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

package com.github.k7t3.tcv.app.service;

import com.github.k7t3.tcv.entity.WindowBoundsEntity;
import com.github.k7t3.tcv.entity.service.WindowBoundsEntityService;
import com.github.k7t3.tcv.view.core.WindowBounds;

public class WindowBoundsService {

    private final WindowBoundsEntityService service;

    public WindowBoundsService(WindowBoundsEntityService service) {
        this.service = service;
    }

    public FXTask<?> saveBoundsAsync(String identity, WindowBounds bounds) {
        var entity = new WindowBoundsEntity(identity,
                bounds.x(), bounds.y(), bounds.width(), bounds.height(), bounds.maximized());
        var t = FXTask.task(() -> service.save(entity));
        t.runAsync();
        return t;
    }

    public WindowBounds getBounds(String identity) {
        var entity = service.get(identity);
        return new WindowBounds(entity.x(), entity.y(),
                entity.width(), entity.height(), entity.maximized());
    }

    public FXTask<WindowBounds> getBoundsAsync(String identity) {
        var t = FXTask.task(() -> getBounds(identity));
        t.runAsync();
        return t;
    }

    public void delete(String identity) {
        FXTask.task(() -> service.delete(identity)).runAsync();
    }

    public void clear() {
        FXTask.task(service::clear).runAsync();
    }

}
