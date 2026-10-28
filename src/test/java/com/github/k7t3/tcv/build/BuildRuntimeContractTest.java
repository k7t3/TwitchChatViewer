package com.github.k7t3.tcv.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import static org.assertj.core.api.Assertions.assertThat;

/** ビルドを起動した JDK にかかわらず、テストと配布は同じJavaを使う。 */
@ExtendWith(ApplicationExtension.class)
class BuildRuntimeContractTest {
    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
        stage.setScene(new Scene(new Label("runtime contract"), 240, 100));
        stage.show();
    }

    @Test
    void applicationTestsRunOnTheJava25Toolchain() {
        assertThat(Runtime.version().feature()).isEqualTo(25);
    }

    @Test
    void theActualGlassBackendMatchesTheRequestedTestMode() throws Exception {
        var requested = System.getProperty("tcv.test.headless");
        assertThat(requested).isIn("true", "false");
        var application = Class.forName("com.sun.glass.ui.Application").getMethod("GetApplication").invoke(null);
        if (Boolean.parseBoolean(requested))
            assertThat(application.getClass().getName()).isEqualTo("com.sun.glass.ui.headless.HeadlessApplication");
        else assertThat(application.getClass().getName()).doesNotContain(".headless.", ".monocle.");
        assertThat(stage.isShowing()).isTrue();
    }
}
