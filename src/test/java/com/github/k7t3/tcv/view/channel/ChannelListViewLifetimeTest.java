package com.github.k7t3.tcv.view.channel;

import com.github.k7t3.tcv.app.channel.TwitchChannelListViewModel;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.app.group.ChannelGroupRepository;
import com.github.k7t3.tcv.app.service.BrowserActions;
import com.github.k7t3.tcv.prefs.AppPreferences;
import com.github.k7t3.tcv.support.MemoryPreferences;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.ButtonType;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(ApplicationExtension.class)
class ChannelListViewLifetimeTest {
    @Test
    void closingTheViewActuallyClosesItsShownYesNoAlert(FxRobot robot) {
        robot.interact(() -> {
            var reference = new AtomicReference<Fixture>(); var alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
            var expired = new java.util.concurrent.atomic.AtomicBoolean();
            var guard = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
            guard.setOnFinished(event -> { expired.set(true); alert.setResult(ButtonType.NO); alert.close(); });
            alert.setOnShown(event -> javafx.application.Platform.runLater(() -> reference.get().controller.close()));
            try (var ui = new Fixture(() -> alert)) {
                reference.set(ui); ui.select(6); guard.play();
                ui.action(false).fire();
                assertThat(expired).isFalse(); assertThat(alert.isShowing()).isFalse();
                verify(ui.model, never()).joinChat(); verify(ui.model, never()).openSelectedChannelPageOnBrowser();
            } finally { guard.stop(); alert.setResult(ButtonType.NO); alert.close(); }
        });
    }

    @ParameterizedTest @CsvSource({"false,0,true,0,0", "true,0,true,0,0", "false,1,true,1,0", "true,1,true,1,0", "false,5,true,1,0", "true,5,true,1,0", "false,6,true,1,1", "true,6,true,1,1", "false,6,false,0,1", "true,6,false,0,1"})
    void openingPreservesTheFiveChannelConfirmationBoundary(boolean browser, int count, boolean yes, int started, int shown, FxRobot robot) {
        robot.interact(() -> {
            var dialog = dialog(); when(dialog.showAndWait()).thenReturn(Optional.of(yes ? ButtonType.YES : ButtonType.NO));
            try (var ui = new Fixture(() -> dialog)) {
                ui.select(count); ui.action(browser).fire();
                verify(ui.model, times(browser ? 0 : started)).joinChat();
                verify(ui.model, times(browser ? started : 0)).openSelectedChannelPageOnBrowser();
                verify(dialog, times(shown)).showAndWait(); verify(dialog, times(shown)).close();
                if (shown != 0) {
                    assertThat(dialog.getDialogPane().getContentText()).contains("6");
                    assertThat(dialog.getDialogPane().getButtonTypes()).containsExactly(ButtonType.YES, ButtonType.NO);
                    verify(dialog).initOwner(ui.stage);
                }
            }
        });
    }

    @ParameterizedTest @CsvSource({"false,false", "true,false", "false,true", "true,true"})
    void reentrantOpeningDuringFactoryOrModalStartsOnlyOneOperation(boolean browser, boolean duringFactory, FxRobot robot) {
        robot.interact(() -> {
            var reference = new AtomicReference<Fixture>(); var created = new java.util.concurrent.atomic.AtomicInteger();
            var dialog = dialog();
            when(dialog.showAndWait()).thenAnswer(call -> {
                if (!duringFactory) reference.get().action(!browser).fire();
                return Optional.of(ButtonType.YES);
            });
            try (var ui = new Fixture(() -> {
                created.incrementAndGet();
                if (duringFactory) reference.get().action(!browser).fire();
                return dialog;
            })) {
                reference.set(ui); ui.select(6); ui.action(browser).fire();
                assertThat(created).hasValue(1); verify(dialog, times(1)).showAndWait();
                verify(ui.model, times(browser ? 0 : 1)).joinChat();
                verify(ui.model, times(browser ? 1 : 0)).openSelectedChannelPageOnBrowser();
            }
        });
    }

    @ParameterizedTest @ValueSource(booleans = {false,true})
    void anEndedBorrowedModelCannotStartAnotherOperation(boolean browser, FxRobot robot) {
        robot.interact(() -> {
            try (var ui = new Fixture()) {
                var action = ui.action(browser).getOnAction();
                ui.model.close(); ui.model.getSelectedChannels().add(mock(TwitchChannelViewModel.class));
                action.handle(new ActionEvent());
                verify(ui.model, never()).joinChat(); verify(ui.model, never()).openSelectedChannelPageOnBrowser();
            }
        });
    }

    @Test
    void retainedClearAndLazyMenuHandlersCannotMutateAClosedView(FxRobot robot) {
        robot.interact(() -> {
            try (var ui = new Fixture()) {
                var search = (atlantafx.base.controls.CustomTextField) ui.stage.getScene().lookup("#searchField");
                var clear = search.getRight().getOnMouseClicked(); var lazyMenu = ui.list.getOnContextMenuRequested();
                ui.controller.close(); ui.model.setFilter("kept");
                clear.handle(mock(javafx.scene.input.MouseEvent.class));
                lazyMenu.handle(mock(javafx.scene.input.ContextMenuEvent.class));
                assertThat(ui.model.getFilter()).isEqualTo("kept"); assertThat(ui.list.getContextMenu()).isNull();
                assertThat(search.getRight()).isNull(); assertThat(ui.list.getOnContextMenuRequested()).isNull();
            }
        });
    }

    @Test
    void theViewEndsExistingCellsAndCellsReturnedLateByItsFactory(FxRobot robot) {
        robot.interact(() -> {
            try (var ui = new Fixture()) {
                ui.select(2); ui.stage.getScene().getRoot().applyCss(); ui.stage.getScene().getRoot().layout();
                var cell = ui.list.lookupAll(".channel-list-cell").stream().map(TwitchChannelListCell.class::cast)
                        .filter(value -> !value.isEmpty()).findFirst().orElseThrow();
                var image = (com.github.k7t3.tcv.view.image.LazyImageView) ((javafx.scene.layout.BorderPane) cell.getGraphic()).getLeft();
                var factory = ui.list.getCellFactory();
                ui.controller.close();
                assertThat(cell.getGraphic()).isNull(); assertThat(image.lazyImageProperty().isBound()).isFalse();
                assertThat(image.getImage()).isNull(); assertThat(image.effectProperty().isBound()).isFalse();
                var late = (TwitchChannelListCell) factory.call(ui.list); var item = new ChannelListCellLifetimeTest.Item("late");
                late.updateItem(item.model, false);
                assertThat(late.getGraphic()).isNull(); verify(item.model, never()).profileImageProperty();
                assertThat(ui.model.isClosed()).isFalse();
            }
        });
    }

    @ParameterizedTest @CsvSource({"false,factory", "true,factory", "false,close", "true,close", "false,selection", "true,selection"})
    void lateConfirmationCannotStartAnOperationAfterItsContextEnds(boolean browser, String ending, FxRobot robot) {
        robot.interact(() -> {
            var reference = new AtomicReference<Fixture>(); var dialog = dialog();
            when(dialog.showAndWait()).thenAnswer(call -> {
                var ui = reference.get();
                if (ending.equals("close")) ui.controller.close();
                else {
                    var original = List.copyOf(ui.model.getSelectedChannels());
                    ui.model.getSelectedChannels().clear(); ui.model.getSelectedChannels().setAll(original);
                }
                return Optional.of(ButtonType.YES);
            });
            try (var ui = new Fixture(() -> {
                if (ending.equals("factory")) reference.get().controller.close();
                return dialog;
            })) {
                reference.set(ui); ui.select(6);
                var menu = ui.list.getContextMenu();
                (browser ? menu.getItems().getLast() : menu.getItems().getFirst()).fire();
                verify(ui.model, never()).joinChat(); verify(ui.model, never()).openSelectedChannelPageOnBrowser();
                verify(dialog, times(1)).close();
                verify(dialog, times(ending.equals("factory") ? 0 : 1)).showAndWait();
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Dialog<ButtonType> dialog() {
        var dialog = (Dialog<ButtonType>) mock(Dialog.class);
        when(dialog.getDialogPane()).thenReturn(new DialogPane()); return dialog;
    }

    @Test
    void closedViewsRejectRetainedMenuAndKeyboardActions(FxRobot robot) {
        robot.interact(() -> {
            try (var ui = new Fixture()) {
                var menu = ui.list.getContextMenu();
                var open = menu.getItems().getFirst().getOnAction();
                var browser = menu.getItems().getLast().getOnAction();
                ui.controller.close();
                ui.model.getSelectedChannels().add(mock(TwitchChannelViewModel.class));

                open.handle(new ActionEvent()); browser.handle(new ActionEvent());
                Event.fireEvent(ui.list, new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));

                verify(ui.model, never()).joinChat();
                verify(ui.model, never()).openSelectedChannelPageOnBrowser();
                assertThat(menu.getItems()).isEmpty();
                assertThat(ui.model.isClosed()).isFalse();
            }
        });
    }

    @Test
    void bothOpeningActionsAreDisabledWithoutASelection(FxRobot robot) {
        robot.interact(() -> {
            try (var ui = new Fixture()) {
                var menu = ui.list.getContextMenu();
                assertThat(menu.getItems().getFirst().isDisable()).isTrue();
                assertThat(menu.getItems().getLast().isDisable()).isTrue();
            }
        });
    }

    static final class Fixture implements AutoCloseable {
        final AppHelper helper = mock(AppHelper.class);
        final AppPreferences preferences = new AppPreferences(new MemoryPreferences());
        final TwitchChannelListViewModel model = spy(new TwitchChannelListViewModel(mock(BrowserActions.class)));
        final TwitchChannelListView controller;
        final Stage stage = new Stage();
        final ListView<TwitchChannelViewModel> list;
        Fixture() { this(() -> { throw new AssertionError("unexpected confirmation"); }); }
        @SuppressWarnings("unchecked")
        Fixture(Supplier<? extends Dialog<ButtonType>> dialogs) {
            when(helper.getChannelGroupRepository()).thenReturn(mock(ChannelGroupRepository.class));
            doNothing().when(model).joinChat(); doNothing().when(model).openSelectedChannelPageOnBrowser();
            controller = new TwitchChannelListView(helper::getChannelGroupRepository, preferences, () -> stage, dialogs);
            var tuple = FluentViewLoader.fxmlView(TwitchChannelListView.class).codeBehind(controller).viewModel(model)
                    .resourceBundle(Resources.getResourceBundle()).load();
            stage.setScene(new Scene(tuple.getView(), 500, 400)); stage.show();
            list = (ListView<TwitchChannelViewModel>) tuple.getView().lookup("#channels");
            when(helper.getPrimaryStage()).thenReturn(stage);
        }
        void select(int count) {
            var items = javafx.collections.FXCollections.<TwitchChannelViewModel>observableArrayList();
            for (int i=0;i<count;i++) items.add(new ChannelListCellLifetimeTest.Item("channel"+i).model);
            model.bindChannels(items); list.getSelectionModel().selectAll();
            assertThat(model.getSelectedChannels()).hasSize(count);
        }
        javafx.scene.control.MenuItem action(boolean browser) {
            var items = list.getContextMenu().getItems(); return browser ? items.getLast() : items.getFirst();
        }
        @Override public void close() { controller.close(); stage.close(); model.close(); }
    }
}
