/* SPDX-License-Identifier: MIT */

package com.github.k7t3.tcv.app.event;

import java.util.UUID;

/**
 * <a href="https://github.com/mkpaz/atlantafx/blob/master/sampler/src/main/java/atlantafx/sampler/event/Event.java">atlantafx</a>
 */
public abstract class Event {

    protected final UUID id = UUID.randomUUID();

    protected Event() {
    }

    public UUID getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Event event)) {
            return false;
        }
        return id.equals(event.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Event{"
            + "id=" + id
            + '}';
    }

    public static <E extends Event> void publish(E event) {
        DefaultEventBus.getInstance().publish(event);
    }
}
