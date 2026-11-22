package com.github.k7t3.tcv.view.core;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

class ViewResourcesTest {
    @Test
    void closingReleasesChildrenBeforeTheirParentsAndAttemptsAllEvenAfterFailures() {
        var resources = new ViewResources(); var released = new ArrayList<String>();
        var first = new IllegalStateException("child failed"); var second = new IllegalArgumentException("parent failed");
        resources.add(() -> { released.add("parent"); throw second; });
        resources.add(() -> released.add("binding"));
        resources.add(() -> { released.add("child"); throw first; });
        assertThatThrownBy(resources::close).isSameAs(first).satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(second));
        assertThat(released).containsExactly("child", "binding", "parent");
        assertThat(resources.isClosed()).isTrue(); resources.close();
        assertThat(released).containsExactly("child", "binding", "parent");
    }

    @Test
    void resourcesReturnedDuringOrAfterAReentrantCloseAreReleasedImmediately() {
        var resources = new ViewResources(); var released = new ArrayList<String>();
        resources.add(() -> { resources.close(); resources.add(() -> released.add("during")); });
        resources.close(); resources.add(() -> released.add("after")); resources.close();
        assertThat(released).containsExactly("during", "after");
    }
}
