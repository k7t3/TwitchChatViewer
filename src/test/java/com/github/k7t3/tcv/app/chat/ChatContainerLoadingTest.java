package com.github.k7t3.tcv.app.chat;

import com.github.k7t3.tcv.app.image.ImageLoader;

import com.github.k7t3.tcv.app.chat.filter.ChatFilters;
import com.github.k7t3.tcv.app.chat.filter.KeywordFilterEntry;
import com.github.k7t3.tcv.app.channel.TwitchChannelViewModel;
import com.github.k7t3.tcv.app.channel.MultipleChatOpenType;
import com.github.k7t3.tcv.app.event.ChatOpeningEvent;
import com.github.k7t3.tcv.app.image.LazyImage;
import com.github.k7t3.tcv.domain.channel.TwitchChannel;
import com.github.k7t3.tcv.domain.chat.ChatData;
import com.github.k7t3.tcv.domain.chat.ChatMessage;
import com.github.k7t3.tcv.domain.chat.ChatMessageFragment;
import com.github.k7t3.tcv.domain.chat.ChatRoom;
import com.github.k7t3.tcv.domain.event.chat.ChatMessageEvent;
import com.github.k7t3.tcv.domain.event.EventSubscribers;
import com.github.k7t3.tcv.app.core.Resources;
import com.github.k7t3.tcv.view.chat.ChatContainerView;
import de.saxsys.mvvmfx.FluentViewLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;
import com.github.k7t3.tcv.app.emoji.Emoji;
import com.github.k7t3.tcv.domain.chat.GlobalChatBadges;
import com.github.k7t3.tcv.entity.ChatKeywordFilterEntity;
import com.github.k7t3.tcv.entity.ChatUserFilterEntity;
import com.github.k7t3.tcv.app.chat.filter.UserFilterEntry;
import javafx.application.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testfx.api.FxRobot;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.CountDownLatch;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(ApplicationExtension.class)
class ChatContainerLoadingTest {

    @Test
    void closingTheImageServiceCancelsQueuedEmojiAndKeepsTheBorrowedWorkerUsable(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var image = new AtomicReference<LazyImage>();
        var emoji = mock(Emoji.class);
        when(emoji.contains("1f642")).thenReturn(true);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor(); var images = new ImageLoader(queue::add);
             var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                     new ChatFilters.Snapshot(List.of(), List.of()), images)) {
            robot.interact(() -> image.set(resources.emojiStore().get("1f642")));

            images.close();
            runNext(queue, worker);

            assertThat(image.get().completion().toCompletableFuture().isCompletedExceptionally()).isTrue();
            robot.interact(() -> assertThat(image.get().getValue()).isNull());
            verify(emoji, never()).openImageStream("1f642");
            verify(emoji, never()).close();
            assertThat(worker.submit(() -> 42).get(5, TimeUnit.SECONDS)).isEqualTo(42);
        }
        verify(emoji).close();
    }

    @Test
    void emojiDecodingUsesTheBorrowedImageWorker(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory,
                                                FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var image = new AtomicReference<LazyImage>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor(); var images = new ImageLoader(queue::add);
             var emoji = new Emoji(directory)) {
            emoji.extractArchive();
            assertThat(emoji.validateArchive()).isTrue();
            try (var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                    new ChatFilters.Snapshot(List.of(), List.of()), images)) {
                robot.interact(() -> {
                    try (var global = mockConstruction(com.github.k7t3.tcv.app.service.TaskWorker.class)) {

                        image.set(resources.emojiStore().get("1f642"));

                        assertThat(queue).hasSize(1);
                        assertThat(global.constructed()).isEmpty();
                    }
                });
                runNext(queue, worker);
                var decoded = image.get().completion().toCompletableFuture().get(5, TimeUnit.SECONDS);
                robot.interact(() -> {
                    assertThat(image.get().getValue()).isSameAs(decoded);
                    assertThat(decoded.isError()).isFalse();
                    assertThat(decoded.getWidth()).isEqualTo(32);
                    assertThat(decoded.getHeight()).isEqualTo(32);
                    assertThat(resources.emojiStore().get("1f642")).isSameAs(image.get());
                });
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void endingSessionCancelsRenderedEmojiBeforeClosingItsArchive(boolean close, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var streamClosed = new CountDownLatch(1);
        var image = new AtomicReference<LazyImage>();
        var cancelledBeforeArchiveClose = new AtomicReference<Boolean>();
        var emoji = mock(Emoji.class);
        when(emoji.contains("1f642")).thenReturn(true);
        when(emoji.openImageStream("1f642")).thenAnswer(invocation -> {
            entered.countDown();
            awaitRelease(release);
            return new ByteArrayInputStream(new byte[] {1, 2, 3}) {
                @Override
                public void close() { streamClosed.countDown(); }
            };
        });
        doAnswer(invocation -> {
            cancelledBeforeArchiveClose.set(image.get().completion().toCompletableFuture().isCompletedExceptionally());
            return null;
        }).when(emoji).close();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor(); var images = new ImageLoader(worker)) {
            var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                    new ChatFilters.Snapshot(List.of(), List.of()), images);
            var model = new ChatRoomContainerViewModel(mock(ChatFilters.class), () -> resources, queue::add);
            var channel = mock(TwitchChannelViewModel.class);
            var domainChannel = mock(TwitchChannel.class);
            when(channel.getChannel()).thenReturn(domainChannel);
            var chatRoom = mock(ChatRoom.class);
            when(chatRoom.getChannel()).thenReturn(domainChannel);
            var fragment = ChatMessageFragment.emoji("🙂", "1f642");
            var data = new ChatData("1", "channel", "message", "2", "Viewer", "viewer", "#ff0000", List.of(),
                    new ChatMessage("🙂", List.of(fragment)), Instant.EPOCH);
            try {
                robot.interact(() -> {
                    model.subscribeEvents(mock(EventSubscribers.class));
                    model.loadAsync();
                });
                runNext(queue, worker);
                robot.interact(() -> {
                    model.publish(new ChatOpeningEvent(channel));
                    var room = model.getChatRoomList().getFirst();
                    room.chatCacheSizeProperty().unbind();
                    room.chatCacheSizeProperty().set(10);
                    room.onChatAdded(new ChatMessageEvent(chatRoom, data), false);
                    image.set(room.getChatDataList().getFirst().getEmojiImage(fragment));
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

                robot.interact(() -> {
                    if (close) model.close();
                    else model.onLogout();
                });
                runNext(queue, worker);

                assertThat(cancelledBeforeArchiveClose.get()).as("image cancellation precedes archive close").isTrue();
                robot.interact(() -> assertThat(image.get().getValue()).isNull());
                verify(emoji).close();
            } finally {
                release.countDown();
                assertThat(streamClosed.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(model::close);
                resources.close();
            }
        }
    }

    @Test
    void invalidStoredFilterPreventsReadinessPreservesListsAndReleasesResources(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var emoji = mock(Emoji.class);
        var filters = new ChatFilters(mock(com.github.k7t3.tcv.entity.service.ChatFilterEntityStore.class), Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        var previousKeyword = KeywordFilterEntry.containsMatch("previous");
        var previousUser = new UserFilterEntry("42", "previous user", "previous comment");
        var snapshot = new ChatFilters.Snapshot(List.of(new ChatKeywordFilterEntity(7, 0, "new"),
                new ChatKeywordFilterEntity(8, 3, "[")), List.of(new ChatUserFilterEntity("43", "new user", "new comment")));
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class), snapshot, mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(filters, () -> resources, queue::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                filters.getKeywordEntries().add(previousKeyword);
                filters.getUserEntries().add(previousUser);
                model.loadAsync();
            });
            runNext(queue, worker);

            robot.interact(() -> {
                assertThat(model.isLoaded()).isFalse();
                assertThat(model.isLoading()).isFalse();
                assertThat(model.getError()).isNotBlank();
                assertThat(filters.getKeywordEntries()).containsExactly(previousKeyword);
                assertThat(filters.getUserEntries()).containsExactly(previousUser);
            });
            runNext(queue, worker);
            verify(emoji).close();
        } finally {
            robot.interact(model::close);
            robot.interact(filters::close);
            resources.close();
        }
    }

    @Test
    void latePreviousSessionResultCannotReplaceNewlyLoadedResources(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var attempts = new AtomicInteger();
        var oldEmoji = mock(Emoji.class);
        var newEmoji = mock(Emoji.class);
        var previous = new ChatResources(oldEmoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(new ChatKeywordFilterEntity(7, 0, "old")), List.of()), mock(ImageLoader.class));
        var current = new ChatResources(newEmoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(new ChatKeywordFilterEntity(8, 0, "new")), List.of()), mock(ImageLoader.class));
        var filters = new ChatFilters(mock(com.github.k7t3.tcv.entity.service.ChatFilterEntityStore.class), Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        var model = new ChatRoomContainerViewModel(filters, () -> {
            if (attempts.incrementAndGet() != 1) return current;
            entered.countDown();
            awaitRelease(release);
            return previous;
        }, queue::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> { model.loadAsync(); });
            var task = queue.poll(5, TimeUnit.SECONDS);
            assertThat(task).isNotNull();
            var oldWork = worker.submit(task);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> { model.onLogout(); model.loadAsync(); });
                runNext(queue, worker);

                release.countDown();
                oldWork.get(5, TimeUnit.SECONDS);

                robot.interact(() -> {
                    assertThat(model.isLoaded()).isTrue();
                    assertThat(model.isLoading()).isFalse();
                    assertThat(filters.getKeywordEntries()).extracting(KeywordFilterEntry::getKeyword)
                            .containsExactly("new");
                });
                verify(oldEmoji).close();
                verify(newEmoji, never()).close();
                robot.interact(model::close);
                runNext(queue, worker);
            } finally {
                release.countDown();
                oldWork.get(5, TimeUnit.SECONDS);
            }
        } finally {
            robot.interact(model::close);
            robot.interact(filters::close);
            previous.close();
            current.close();
        }
    }

    @Test
    void logoutDiscardsChatOpeningRequestsFromThePreviousSession(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var resources = new ChatResources(mock(Emoji.class), mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(mock(ChatFilters.class), () -> resources, queue::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                model.subscribeEvents(mock(EventSubscribers.class));
                model.loadAsync();
                model.publish(new ChatOpeningEvent(mock(TwitchChannelViewModel.class)));
                model.onLogout();
                model.subscribeEvents(mock(EventSubscribers.class));
                model.loadAsync();
            });
            runNext(queue, worker);
            runNext(queue, worker);

            robot.interact(() -> {
                assertThat(model.isLoaded()).isTrue();
                assertThat(model.getChatRoomList()).isEmpty();
                model.close();
            });
            runNext(queue, worker);
        } finally {
            robot.interact(model::close);
            resources.close();
        }
    }

    @Test
    void repeatedLoadWhileQueuedSharesTheSameOperation(FxRobot robot) {
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new ChatRoomContainerViewModel(mock(ChatFilters.class), () -> {
            throw new AssertionError("queued work must not run");
        }, queue::add);
        try {
            robot.interact(() -> {
                var first = model.loadAsync();
                var second = model.loadAsync();

                assertThat(second).isSameAs(first);
                assertThat(model.isLoading()).isTrue();
                assertThat(queue).hasSize(1);
            });
        } finally {
            robot.interact(model::close);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void endingSessionDuringLoadDisposesAResultThatIgnoresInterruption(boolean close, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var emoji = mock(Emoji.class);
        var filters = mock(ChatFilters.class);
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(filters, () -> {
            entered.countDown();
            awaitRelease(release);
            return resources;
        }, queue::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> { model.loadAsync(); });
            var task = queue.poll(5, TimeUnit.SECONDS);
            assertThat(task).isNotNull();
            var running = worker.submit(task);
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                robot.interact(() -> {
                    if (close) model.close();
                    else model.onLogout();
                });

                release.countDown();
                running.get(5, TimeUnit.SECONDS);

                robot.interact(() -> {
                    assertThat(model.isLoaded()).isFalse();
                    assertThat(model.isLoading()).isFalse();
                });
                verify(filters, never()).applySnapshot(any());
                verify(emoji).close();
            } finally {
                release.countDown();
                running.get(5, TimeUnit.SECONDS);
            }
        } finally {
            robot.interact(model::close);
            resources.close();
        }
    }

    private static void awaitRelease(CountDownLatch release) {
        var interrupted = false;
        try {
            while (release.getCount() != 0) {
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("provider was not released");
                } catch (InterruptedException cancellation) {
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    @Test
    void logoutClearsThePreviousLoadError(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new ChatRoomContainerViewModel(mock(ChatFilters.class), () -> {
            throw new IOException("previous session could not load resources");
        }, queue::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> { model.loadAsync(); });
            runNext(queue, worker);
            robot.interact(() -> {
                assertThat(model.getError()).isNotBlank();

                model.onLogout();

                assertThat(model.getError()).isNull();
                assertThat(model.isLoaded()).isFalse();
                assertThat(model.isLoading()).isFalse();
            });
        } finally {
            robot.interact(model::close);
        }
    }

    private Stage stage;

    @Start
    void start(Stage stage) {
        this.stage = stage;
    }

    @Test
    void failedLoadIsShownInTheChatViewAndCanBeRetried(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new ChatRoomContainerViewModel(mock(ChatFilters.class), () -> {
            throw new IOException("chat resources are unavailable");
        }, queue::add);
        var view = new AtomicReference<Parent>();
        var controller = new AtomicReference<ChatContainerView>();
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                controller.set(new ChatContainerView(mock(com.github.k7t3.tcv.app.group.ChannelGroupRepository.class),
                        mock(com.github.k7t3.tcv.prefs.ChatPreferences.class),
                        () -> mock(com.github.k7t3.tcv.app.service.WindowBoundsService.class)));
                view.set(FluentViewLoader.fxmlView(ChatContainerView.class).codeBehind(controller.get()).viewModel(model)
                        .resourceBundle(Resources.getResourceBundle()).load().getView());
                stage.setScene(new Scene(view.get(), 600, 380));
                stage.show();
                view.get().applyCss();
                model.loadAsync();
            });
            runNext(queue, worker);

            robot.interact(() -> {
                var message = (Label) view.get().lookup("#loadMessage");
                assertThat(message).isNotNull();
                assertThat(view.get().lookup("#loadStatePane").isVisible()).isTrue();
                assertThat(message.getText()).isEqualTo(model.getError());
                var progress = (ProgressIndicator) view.get().lookup("#loadProgress");
                assertThat(progress.isVisible()).isFalse();
                var retry = (Button) view.get().lookup("#retryLoadButton");
                assertThat(retry.isVisible()).isTrue();

                retry.fire();

                assertThat(queue).hasSize(1);
                assertThat(model.isLoading()).isTrue();
                assertThat(progress.isVisible()).isTrue();
                assertThat(retry.isVisible()).isFalse();
                assertThat(message.getText()).isEqualTo(Resources.getString("chat.load.loading"));
            });
        } finally {
            robot.interact(() -> com.github.k7t3.tcv.lifecycle.CloseActions.runAll(
                    () -> { if (controller.get() != null) controller.get().close(); }, model::close,
                    () -> { stage.hide(); stage.setScene(null); }));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void endingTheSessionCancelsQueuedLoadBeforeCallingItsProvider(boolean close, FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var calls = new AtomicInteger();
        var model = new ChatRoomContainerViewModel(mock(ChatFilters.class), () -> {
            calls.incrementAndGet();
            return new ChatResources(mock(Emoji.class), mock(GlobalChatBadges.class),
                    new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        }, queue::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                var task = model.loadAsync();
                if (close) model.close();
                else model.onLogout();

                assertThat(task.isCancelled()).isTrue();
                assertThat(model.isLoaded()).isFalse();
                assertThat(model.isLoading()).isFalse();
                assertThat(model.isClosed()).isEqualTo(close);
            });
            runNext(queue, worker);

            assertThat(calls.get()).isZero();
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void closingBeforeUiDeliveryReleasesTheUnadoptedResources(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var emoji = mock(Emoji.class);
        var filters = mock(ChatFilters.class);
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(filters, () -> resources, queue::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                model.loadAsync();
                try {
                    runNext(queue, worker);
                } catch (Exception failure) {
                    throw new AssertionError(failure);
                }
                model.close();
            });
            runNext(queue, worker);

            robot.interact(() -> assertThat(model.isLoaded()).isFalse());
            verify(filters, never()).applySnapshot(any());
            verify(emoji).close();
        } finally {
            robot.interact(model::close);
            resources.close();
        }
    }

    @Test
    void loadFailureIsObservableAndAnotherAttemptCanSucceed(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var attempts = new AtomicInteger();
        var resources = new ChatResources(mock(Emoji.class), mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var model = new ChatRoomContainerViewModel(mock(ChatFilters.class), () -> {
            if (attempts.incrementAndGet() == 1) throw new IOException("global badges could not be read");
            return resources;
        }, queue::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> { model.loadAsync(); });
            runNext(queue, worker);
            robot.interact(() -> {
                assertThat(model.isLoaded()).isFalse();
                assertThat(model.isLoading()).isFalse();
                assertThat(model.getError()).isNotBlank();

                model.loadAsync();

                assertThat(model.isLoading()).isTrue();
                assertThat(model.getError()).isNull();
            });
            runNext(queue, worker);
            robot.interact(() -> {
                assertThat(model.isLoaded()).isTrue();
                assertThat(model.isLoading()).isFalse();
                model.close();
            });
            runNext(queue, worker);
        } finally {
            robot.interact(model::close);
            resources.close();
        }
    }

    @Test
    void rejectedLoadStopsWaitingAndReportsAnError(FxRobot robot) {
        var attempts = new AtomicInteger();
        var model = new ChatRoomContainerViewModel(mock(ChatFilters.class), () -> {
            attempts.incrementAndGet();
            throw new AssertionError("rejected work must not run");
        }, task -> { throw new RejectedExecutionException("worker is closed"); });
        try {
            robot.interact(() -> {
                var task = model.loadAsync();

                assertThat(task.isCancelled()).isTrue();
                assertThat(model.isLoaded()).isFalse();
                assertThat(model.isLoading()).isFalse();
                assertThat(model.getError()).isNotBlank();
            });
            assertThat(attempts.get()).isZero();
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void chatOpeningDuringLoadingUsesTheChannelsSelectedAtRequestTime(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var resources = new ChatResources(mock(Emoji.class), mock(GlobalChatBadges.class),
                new ChatFilters.Snapshot(List.of(), List.of()), mock(ImageLoader.class));
        var channel = mock(TwitchChannelViewModel.class);
        var selection = new ArrayList<>(List.of(channel));
        var model = new ChatRoomContainerViewModel(mock(ChatFilters.class), () -> resources, queue::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                model.subscribeEvents(mock(EventSubscribers.class));
                model.loadAsync();
                model.publish(new ChatOpeningEvent(MultipleChatOpenType.SEPARATED, selection));
                selection.clear();
                assertThat(model.getChatRoomList()).isEmpty();
            });

            runNext(queue, worker);

            robot.interact(() -> {
                assertThat(model.getChatRoomList()).hasSize(1);
                assertThat(model.getChatRoomList().getFirst()).isInstanceOf(SingleChatRoomViewModel.class);
                assertThat(((SingleChatRoomViewModel) model.getChatRoomList().getFirst()).getChannel()).isSameAs(channel);
                model.close();
            });
            runNext(queue, worker);
        } finally {
            robot.interact(model::close);
        }
    }

    @Test
    void successfulLoadAppliesFiltersOnFxAndOwnsTheArchiveUntilClose(FxRobot robot) throws Exception {
        var queue = new LinkedBlockingQueue<Runnable>();
        var emoji = mock(Emoji.class);
        var filters = new ChatFilters(mock(com.github.k7t3.tcv.entity.service.ChatFilterEntityStore.class), Runnable::run, ignored -> {}, unexpectedServiceFailure -> { throw new AssertionError(unexpectedServiceFailure); });
        var snapshot = new ChatFilters.Snapshot(List.of(new ChatKeywordFilterEntity(7, 0, "blocked")), List.of());
        var resources = new ChatResources(emoji, mock(GlobalChatBadges.class), snapshot, mock(ImageLoader.class));
        var readyOnFx = new AtomicReference<Boolean>();
        var model = new ChatRoomContainerViewModel(filters, () -> {
            assertThat(Platform.isFxApplicationThread()).isFalse();
            return resources;
        }, queue::add);
        try (var worker = Executors.newVirtualThreadPerTaskExecutor()) {
            robot.interact(() -> {
                model.loadedProperty().addListener((observable, old, ready) -> {
                    if (ready) readyOnFx.set(Platform.isFxApplicationThread());
                });
                model.loadAsync();
            });

            runNext(queue, worker);

            robot.interact(() -> {
                assertThat(model.isLoaded()).isTrue();
                assertThat(model.isLoading()).isFalse();
                assertThat(model.getError()).isNull();
                assertThat(readyOnFx.get()).isTrue();
                assertThat(filters.getKeywordEntries()).extracting(KeywordFilterEntry::getFilterId,
                        KeywordFilterEntry::getKeyword).containsExactly(org.assertj.core.groups.Tuple.tuple(7, "blocked"));
                model.close();
            });
            runNext(queue, worker);
            verify(emoji).close();
        } finally {
            robot.interact(model::close);
            robot.interact(filters::close);
        }
    }

    private static void runNext(LinkedBlockingQueue<Runnable> queue, ExecutorService worker) throws Exception {
        var task = queue.poll(5, TimeUnit.SECONDS);
        assertThat(task).isNotNull();
        worker.submit(task).get(5, TimeUnit.SECONDS);
    }

    @Test
    void queuedLoadDoesNotClaimThatChatResourcesAreReady(FxRobot robot) {
        var queue = new LinkedBlockingQueue<Runnable>();
        var model = new ChatRoomContainerViewModel(mock(ChatFilters.class), () -> {
            throw new AssertionError("queued work must not run on the UI thread");
        }, queue::add);
        try {
            robot.interact(() -> {
                var task = model.loadAsync();

                assertThat(model.isLoaded()).isFalse();
                assertThat(task.isDone()).isFalse();
                assertThat(queue).hasSize(1);
            });
        } finally {
            robot.interact(model::close);
        }
    }
}
