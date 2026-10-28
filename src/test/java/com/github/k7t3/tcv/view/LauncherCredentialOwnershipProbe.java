package com.github.k7t3.tcv.view;

import com.github.k7t3.tcv.app.auth.AuthenticatorViewModel;
import com.github.k7t3.tcv.app.core.AppHelper;
import com.github.k7t3.tcv.app.auth.CredentialReadProbeViewModel;
import com.github.k7t3.tcv.app.auth.PreferencesCredentialStorage;
import com.github.k7t3.tcv.app.main.MainViewModel;
import com.github.k7t3.tcv.app.secure.KeyManagerFactory;
import com.github.k7t3.tcv.domain.auth.CredentialStore;
import com.github.philippheuer.credentialmanager.domain.Credential;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.prefs.Preferences;
import javafx.application.Platform;
import javafx.stage.Stage;

import static org.mockito.Mockito.*;

/** 実ユーザーの保存先を使わず、実FXMLから永続鍵による資格情報の読取を2回行う。 */
public final class LauncherCredentialOwnershipProbe {
    public static void main(String[] arguments) throws Exception {
        var root = Path.of(arguments[0]);
        Files.createDirectories(root.resolve("tmp"));
        System.setProperty("user.home", root.toString());
        System.setProperty("java.io.tmpdir", root.resolve("tmp").toString());
        System.setProperty("java.util.prefs.PreferencesFactory", LauncherServiceOwnershipProbe.IsolatedPreferences.class.getName());
        System.setProperty("key.manager.path", root.resolve("keys.p12").toString());
        System.setProperty("key.manager.password", "test-password");
        var node = Preferences.userRoot().node("/com/github/k7t3/tcv");
        var original = new OAuth2Credential("twitch", "dummy-access", "dummy-refresh", "123", "Dummy",
                3600, List.of("chat:read"), Map.of());
        original.setReceivedAt(Instant.EPOCH);
        try (var cipher = KeyManagerFactory.create()) {
            new PreferencesCredentialStorage(node, cipher::encrypt, cipher::decrypt).saveCredentials(List.of(original));
        }
        var encrypted = node.getByteArray("credentials", null);
        var ready = new CountDownLatch(1);
        Platform.startup(() -> { Platform.setImplicitExit(false); ready.countDown(); });
        try {
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("toolkit did not start");
            if (arguments.length > 1 && arguments[1].equals("key-lock-cancel")) {
                verifyKeyLockCancellation(root);
                return;
            }
            if (arguments.length > 1 && arguments[1].equals("process-lock")) {
                verifyProcessExclusion(root, false);
                return;
            }
            if (arguments.length > 1 && arguments[1].equals("shared-settings-lock")) {
                verifyProcessExclusion(root, true);
                return;
            }
            if (arguments.length > 1 && arguments[1].equals("blocked-close")) {
                verifyAcceptedReadBeforeKeyShutdown();
                return;
            }
            for (int index = 1; index <= 2; index++) {
                var state = new ReadState();
                var launcher = launcher(state, null, () -> {});
                launcher.init();
                var shown = new CompletableFuture<Void>();
                Platform.runLater(() -> {
                    try {
                        var stage = new Stage();
                        launcher.start(stage);
                        if (!stage.isShowing()) throw new AssertionError("credential window was not shown");
                        shown.complete(null);
                    } catch (Throwable failure) { shown.completeExceptionally(failure); }
                });
                shown.get(10, TimeUnit.SECONDS);
                final List<Credential> credentials;
                try { credentials = state.loaded.get(10, TimeUnit.SECONDS); }
                catch (ExecutionException failure) {
                    throw new AssertionError("credential read failed for launcher " + index, failure.getCause());
                }
                if (credentials.size() != 1 || !(credentials.getFirst() instanceof OAuth2Credential credential)
                        || !"dummy-access".equals(credential.getAccessToken())) {
                    throw new AssertionError("stored credential changed for launcher " + index);
                }
                var stopped = new CompletableFuture<Void>();
                Platform.runLater(() -> {
                    try {
                        launcher.stop();
                        if (!state.authentication.get().isClosed()) throw new AssertionError("authentication owner was not released");
                        stopped.complete(null);
                    } catch (Throwable failure) { stopped.completeExceptionally(failure); }
                });
                stopped.get(10, TimeUnit.SECONDS);
                if (!state.errors.isEmpty() || !Arrays.equals(encrypted, node.getByteArray("credentials", null))) {
                    throw new AssertionError("credential read modified storage or failed during startup");
                }
                System.out.println("credential read succeeded for launcher " + index);
            }
            System.out.println("stored credentials survived two launcher lifetimes");
        } finally { Platform.exit(); }
    }

    private static void verifyKeyLockCancellation(Path root) throws Exception {
        var keyPath = Path.of(System.getProperty("key.manager.path"));
        var before = Files.readAllBytes(keyPath);
        var lock = keyPath.resolveSibling(keyPath.getFileName() + ".lock");
        var ready = root.resolve("key-lock.ready");
        var state = new ReadState();
        var entered = new CountDownLatch(1);
        var launcher = launcher(state, null, entered::countDown);
        var stopped = new CompletableFuture<Void>();
        try (var watcher = root.getFileSystem().newWatchService()) {
            root.register(watcher, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);
            var holder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), CredentialLockHolder.class.getName(),
                    lock.toString(), ready.toString()).redirectErrorStream(true)
                    .redirectOutput(root.resolve("key-lock-holder.log").toFile()).start();
            boolean started = false;
            try {
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(ready)) {
                    var remaining = deadline - System.nanoTime();
                    if (remaining <= 0) throw new AssertionError("key lock holder did not start");
                    var event = watcher.poll(remaining, TimeUnit.NANOSECONDS);
                    if (event == null) throw new AssertionError("key lock holder did not start");
                    event.pollEvents();event.reset();
                }
                launcher.init();
                var shown = new CompletableFuture<Void>();
                Platform.runLater(() -> {
                    try { launcher.start(new Stage());shown.complete(null); }
                    catch (Throwable failure) { shown.completeExceptionally(failure); }
                });
                started = true;shown.get(10, TimeUnit.SECONDS);
                if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("credential read did not start");
                try { state.loaded.get(250, TimeUnit.MILLISECONDS);throw new AssertionError("credential read bypassed key lock"); }
                catch (TimeoutException expected) { }

                Platform.runLater(() -> {
                    try { launcher.stop();stopped.complete(null); }
                    catch (Throwable failure) { stopped.completeExceptionally(failure); }
                });
                stopped.get(2, TimeUnit.SECONDS);
                if (!holder.isAlive()) throw new AssertionError("key lock owner exited before cancellation");
                try { state.loaded.get(5, TimeUnit.SECONDS);throw new AssertionError("cancelled key read returned credentials"); }
                catch (ExecutionException failure) {
                    if (!(failure.getCause() instanceof com.github.k7t3.tcv.app.secure.KeyManagerException)) throw failure;
                }
            } finally {
                if (holder.isAlive()) { holder.getOutputStream().write('x');holder.getOutputStream().flush(); }
                try {
                    if (!holder.waitFor(5, TimeUnit.SECONDS)) { holder.destroyForcibly();holder.waitFor(5, TimeUnit.SECONDS); }
                } finally {
                    holder.getOutputStream().close();holder.getInputStream().close();holder.getErrorStream().close();
                    if (started && !stopped.isDone()) {
                        Platform.runLater(() -> {
                            try { launcher.stop();stopped.complete(null); }
                            catch (Throwable failure) { stopped.completeExceptionally(failure); }
                        });
                    }
                    if (started) stopped.get(10, TimeUnit.SECONDS);
                }
            }
        }
        if (!Arrays.equals(before, Files.readAllBytes(keyPath)) || !state.authentication.get().isClosed() || !state.errors.isEmpty()) {
            throw new AssertionError("key lock cancellation changed saved keys or lost shutdown ownership");
        }
        System.out.println("FX shutdown cancelled key lock wait before independent lock release");
    }

    private static void verifyProcessExclusion(Path root, boolean sharedSettings) throws Exception {
        var lock = sharedSettings ? new com.github.k7t3.tcv.prefs.AppPreferences().settingsLockFile().orElseThrow()
                : com.github.k7t3.tcv.app.core.OS.current().getApplicationDirectory().resolve("credentials.lock");
        var ready = root.resolve("credential-lock.ready");
        var state = new ReadState();
        var entered = new CountDownLatch(1);
        var launcher = launcher(state, null, entered::countDown);
        // この診断は資格情報の入口を検証する。起動時の設定復旧が同じ排他で待つこととは分ける。
        if (sharedSettings) launcher.init();
        try (var watcher = root.getFileSystem().newWatchService()) {
            root.register(watcher, java.nio.file.StandardWatchEventKinds.ENTRY_CREATE);
            var holder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), CredentialLockHolder.class.getName(),
                    lock.toString(), ready.toString()).redirectErrorStream(true)
                    .redirectOutput(root.resolve("credential-lock.log").toFile()).start();
            try {
                var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!Files.exists(ready)) {
                    var remaining = deadline - System.nanoTime();
                    if (remaining <= 0) throw new AssertionError("lock holder did not start");
                    var event = watcher.poll(remaining, TimeUnit.NANOSECONDS);
                    if (event == null) throw new AssertionError("lock holder did not start");
                    event.pollEvents();event.reset();
                }
                if (!sharedSettings) launcher.init();
                var shown = new CompletableFuture<Void>();
                Platform.runLater(() -> {
                    try { launcher.start(new Stage());shown.complete(null); }
                    catch (Throwable failure) { shown.completeExceptionally(failure); }
                });
                shown.get(10, TimeUnit.SECONDS);
                if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("credential worker did not start");
                try {
                    state.loaded.get(250, TimeUnit.MILLISECONDS);
                    throw new AssertionError("production credential read bypassed the independent process lock");
                } catch (TimeoutException expected) { }
            } finally {
                if (holder.isAlive()) {
                    holder.getOutputStream().write('x');holder.getOutputStream().flush();
                }
                try {
                    if (!holder.waitFor(5, TimeUnit.SECONDS)) { holder.destroyForcibly();holder.waitFor(5, TimeUnit.SECONDS); }
                } finally {
                    holder.getOutputStream().close();holder.getInputStream().close();holder.getErrorStream().close();
                    // 再開を確認してから終了する。stopによる正しい取消と競合させない。
                    try { state.loaded.get(10, TimeUnit.SECONDS); }
                    finally {
                        var stopped = new CompletableFuture<Void>();
                        Platform.runLater(() -> {
                            try { launcher.stop();stopped.complete(null); }
                            catch (Throwable failure) { stopped.completeExceptionally(failure); }
                        });
                        stopped.get(10, TimeUnit.SECONDS);
                    }
                }
            }
        }
        var credentials = state.loaded.get(10, TimeUnit.SECONDS);
        if (credentials.size() != 1 || !(credentials.getFirst() instanceof OAuth2Credential credential)
                || !"dummy-access".equals(credential.getAccessToken()) || !state.errors.isEmpty()) {
            throw new AssertionError("production credential read did not resume with the saved value");
        }
        System.out.println(sharedSettings ? "production credential read waited for the shared settings process lock"
                : "production credential read waited for the independent process lock");
    }

    public static final class CredentialLockHolder {
        public static void main(String[] args) throws Exception {
            var lock = Path.of(args[0]);Files.createDirectories(lock.getParent());
            try (var channel = java.nio.channels.FileChannel.open(lock, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.WRITE);var held = channel.lock()) {
                Files.createFile(Path.of(args[1]));
                if (System.in.read() != 'x') throw new AssertionError("lock holder not released");
            }
        }
    }

    private static final class ReadState {
        final CompletableFuture<List<Credential>> loaded = new CompletableFuture<>();
        final AtomicReference<AuthenticatorViewModel> authentication = new AtomicReference<>();
        final AtomicReference<CredentialStore> store = new AtomicReference<>();
        final List<Throwable> errors = new CopyOnWriteArrayList<>();
    }

    private static Launcher launcher(ReadState state, AppHelper helper, Runnable beforeRead) {
        return new Launcher(null, helper, () -> {}, state.errors::add, () -> {},
                owner -> MainModelFactory.create(owner, state.errors::add, store -> {
                    state.store.set(store);
                    var model = new CredentialReadProbeViewModel(store, owner.getExecutor(), state.loaded, beforeRead);
                    state.authentication.set(model);
                    return model;
                }), null);
    }

    private static void verifyAcceptedReadBeforeKeyShutdown() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var closing = new CountDownLatch(1);
        var helper = spy(new AppHelper(unexpectedApplicationFailure -> { throw new AssertionError(unexpectedApplicationFailure); }));
        doAnswer(call -> { closing.countDown(); return call.callRealMethod(); }).when(helper).close();
        var state = new ReadState();
        var launcher = launcher(state, helper, () -> {
            entered.countDown();
            // 取消で中断できない認証I/Oの境界を再現する。
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (true) {
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new AssertionError("credential read was not released");
                try {
                    if (release.await(remaining, TimeUnit.NANOSECONDS)) return;
                    throw new AssertionError("credential read was not released");
                } catch (InterruptedException ignored) { }
            }
        });
        launcher.init();
        var shown = new CompletableFuture<Void>();
        Platform.runLater(() -> {
            try { launcher.start(new Stage()); shown.complete(null); }
            catch (Throwable failure) { shown.completeExceptionally(failure); }
        });
        shown.get(10, TimeUnit.SECONDS);
        var stopped = new CompletableFuture<Void>();
        try {
            if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("credential read did not start");
            Platform.runLater(() -> {
                try { launcher.stop(); stopped.complete(null); }
                catch (Throwable failure) { stopped.completeExceptionally(failure); }
            });
            if (!closing.await(5, TimeUnit.SECONDS)) throw new AssertionError("application release did not start");
            try {
                stopped.get(150, TimeUnit.MILLISECONDS);
                throw new AssertionError("application stopped before the accepted credential read completed");
            } catch (TimeoutException expected) { }
        } finally { release.countDown(); }
        var credentials = state.loaded.get(10, TimeUnit.SECONDS);
        stopped.get(10, TimeUnit.SECONDS);
        if (credentials.size() != 1 || !(credentials.getFirst() instanceof OAuth2Credential credential)
                || !"dummy-access".equals(credential.getAccessToken()) || !state.authentication.get().isClosed()) {
            throw new AssertionError("accepted credential read was not completed before shutdown");
        }
        try {
            state.store.get().loadCredentials();
            throw new AssertionError("credential keys remained open after application shutdown");
        } catch (IllegalStateException expected) { }
        System.out.println("accepted credential read finished before key shutdown");
    }

}
