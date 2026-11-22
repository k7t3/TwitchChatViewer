package com.github.k7t3.tcv.app.image;

import com.github.k7t3.tcv.lifecycle.CloseActions;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

/** URLと入力streamの画像を読み込み、未完了画像を所有する。executorは所有者から借用する。 */
public final class ImageLoader implements AutoCloseable {
    private final ImageDownloader downloader = new ImageDownloader(Duration.ofSeconds(15));
    private final Executor executor;
    private final Object lifecycle = new Object();
    private final Set<LazyImage> pending = ConcurrentHashMap.newKeySet();
    private boolean closed;

    /** @param executor FX スレッドとは別のスレッドで画像を処理する executor */
    public ImageLoader(Executor executor) {
        this.executor = Objects.requireNonNull(executor);
    }

    /**
     * HTTP 画像の読み込みを開始する。FX スレッドから呼び出す。
     * @param url 取得する HTTP または HTTPS の URL
     * @param width 要求幅。0 の場合は元画像の幅
     * @param height 要求高さ。0 の場合は元画像の高さ
     * @return 読み込みの結果を観測する画像
     * @throws IllegalStateException このサービスが終了している場合
     */
    public LazyImage load(String url, double width, double height) {
        return decode(() -> new ByteArrayInputStream(downloader.download(URI.create(url))), width, height);
    }

    /**
     * 借用executorで入力streamを開き、画像をデコードする。FXスレッドから呼び出す。
     * @param input streamの提供元。取得したstreamは成功・失敗によらず閉じる
     * @param width 要求幅。0の場合は元画像の幅
     * @param height 要求高さ。0の場合は元画像の高さ
     * @return FX上での反映と失敗・取消を観測する画像
     * @throws IllegalStateException このサービスが終了している場合
     */
    public LazyImage decode(Callable<InputStream> input, double width, double height) {
        synchronized (lifecycle) {
            if (closed) throw new IllegalStateException("image loader is closed");
            var image = new LazyImage(Objects.requireNonNull(input), width, height, executor);
            if (closed) {
                image.close();
                return image;
            }
            pending.add(image);
            image.completion().whenComplete((value, failure) -> pending.remove(image));
            return image;
        }
    }

    /**
     * 未完了・UI反映待ちの画像を取り消し、HTTPクライアントを終了する。
     * 借用executorは終了しない。入力streamの実処理の終了待ちはexecutorの所有者が行う。
     */
    @Override
    public void close() {
        final List<LazyImage> owned;
        synchronized (lifecycle) {
            if (closed) return;
            closed = true;
            owned = List.copyOf(pending);
            pending.clear();
        }
        CloseActions.runAll(
                () -> CloseActions.runAll(owned.stream().map(image -> (Runnable) image::close).toArray(Runnable[]::new)),
                downloader::close
        );
    }
}
