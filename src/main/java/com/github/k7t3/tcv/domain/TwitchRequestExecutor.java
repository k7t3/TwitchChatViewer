/*
 * Copyright 2026 k7t3
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

package com.github.k7t3.tcv.domain;

import com.github.twitch4j.common.exception.UnauthorizedException;
import feign.FeignException;
import feign.RetryableException;
import feign.codec.DecodeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import com.github.k7t3.tcv.domain.exception.InvalidCredentialException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** 呼出元のスレッドで API を実行し、同時実行数・再試行・割り込みを管理する。 */
final class TwitchRequestExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TwitchRequestExecutor.class);
    private static final int MAX_REQUEST_ATTEMPTS = 5;
    private static final long RETRY_DELAY_MILLIS = 250;

    private final Semaphore semaphore = new Semaphore(4, true);

    <T> T execute(Supplier<T> request, Runnable refreshAccessToken) {
        for (var attempt = 1; ; attempt++) {
            try {
                if (attempt > 1) Thread.sleep(RETRY_DELAY_MILLIS << (attempt - 2));
                semaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }

            try {
                return request.get();
            } catch (CancellationException | InvalidCredentialException e) {
                throw e;
            } catch (Exception e) {
                var cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;
                if (cause instanceof CancellationException cancelled) throw cancelled;
                if (cause instanceof DecodeException decoded
                        && decoded.getCause() instanceof CancellationException cancelled) throw cancelled;
                if (cause instanceof InterruptedException || cause.getCause() instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                if (attempt >= MAX_REQUEST_ATTEMPTS) throw new RuntimeException(e);
                if (cause instanceof UnauthorizedException) {
                    refreshAccessToken.run();
                    continue;
                }

                if (cause instanceof TimeoutException) {
                    LOGGER.warn("Twitch API request timed out");
                    continue;
                }

                if (cause instanceof FeignException fe) {
                    LOGGER.warn("Twitch API request failed with status {}", fe.status());
                    if (500 <= fe.status() && fe.status() <= 599) continue;
                    if (fe.status() == 429 || fe.status() < 0 && fe instanceof RetryableException) continue;
                }

                LOGGER.error("Twitch API request failed", e);
                throw new RuntimeException(e);
            } finally {
                // 次の試行と待機の前に通信枠を返し、他のリクエストを妨げない。
                semaphore.release();
            }
        }
    }
}
