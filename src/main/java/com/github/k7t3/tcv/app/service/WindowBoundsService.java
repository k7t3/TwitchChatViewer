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

    public FXTask<WindowBounds> getBoundsAsync(String identity) {
        var t = FXTask.task(() -> {
            var entity = service.get(identity);
            return new WindowBounds(entity.x(), entity.y(),
                    entity.width(), entity.height(), entity.maximized());
        });
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
