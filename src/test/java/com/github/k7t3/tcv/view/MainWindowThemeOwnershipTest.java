package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.service.WindowBoundsService;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import com.github.k7t3.tcv.view.core.WindowBounds;
import com.github.k7t3.tcv.view.main.MainView;
import javafx.application.Application;
import javafx.beans.property.SimpleStringProperty;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class MainWindowThemeOwnershipTest {
    @Test
    void closingOneMainWindowDoesNotPreventAnotherWindowFromApplyingItsTheme(FxRobot robot) {
        robot.interact(() -> {
            var previous = Application.getUserAgentStylesheet();
            var helper = mock(AppHelper.class);
            var bounds = mock(WindowBoundsService.class);
            when(helper.getWindowBoundsService()).thenReturn(bounds);
            var model = mock(MainViewModel.class);
            when(model.titleProperty()).thenReturn(new SimpleStringProperty("Theme ownership"));
            var preferences = new AppPreferences(new MemoryPreferences());
            var firstStage = new Stage();
            var secondStage = new Stage();
            MainWindow first = null;
            MainWindow second = null;
            try (var controllers = mockConstruction(MainView.class)) {
                first = new MainWindow(firstStage, model, helper, preferences,
                        new WindowBounds(20, 20, 600, 400, false), () -> {},
                        failure -> { throw new AssertionError(failure); }, mock(com.github.k7t3.tcv.domain.auth.CredentialStore.class));
                first.show();
                first.close();

                second = new MainWindow(secondStage, model, helper, preferences,
                        new WindowBounds(40, 40, 600, 400, false), () -> {},
                        failure -> { throw new AssertionError(failure); }, mock(com.github.k7t3.tcv.domain.auth.CredentialStore.class));
                second.show();

                assertThat(firstStage.isShowing()).isFalse();
                assertThat(secondStage.isShowing()).isTrue();
                assertThat(secondStage.getScene().getRoot()).isNotNull();
            } finally {
                try { if (second != null) second.close(); }
                finally {
                    try { if (first != null) first.close(); }
                    finally {
                        firstStage.close();
                        secondStage.close();
                        Application.setUserAgentStylesheet(previous);
                    }
                }
            }
        });
    }
}
