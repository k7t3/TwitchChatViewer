package com.github.k7t3.tcv.reactive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import static org.assertj.core.api.Assertions.assertThat;

class AbstractSubscriberTest {

    @Test
    void duplicateSubscriptionIsCancelledWithoutReplacingOriginal() {
        var subscriber = new RecordingSubscriber();
        var original = new RecordingSubscription();
        var duplicate = new RecordingSubscription();
        subscriber.setBackPressurePolicy(BackPressurePolicy.MINIMUM);
        subscriber.onSubscribe(original);

        subscriber.onSubscribe(duplicate);
        subscriber.onNext("message");
        subscriber.cancel();

        assertThat(original.requests).containsExactly(1L, 1L);
        assertThat(original.cancelCount).isEqualTo(1);
        assertThat(duplicate.requests).isEmpty();
        assertThat(duplicate.cancelCount).isEqualTo(1);
        assertThat(subscriber.items).containsExactly("message");
    }

    @ParameterizedTest
    @EnumSource(BackPressurePolicy.class)
    void requestsNextSegmentAfterConsumingCurrentSegment(BackPressurePolicy policy) {
        var subscriber = new RecordingSubscriber();
        var subscription = new RecordingSubscription();
        subscriber.setBackPressurePolicy(policy);
        subscriber.onSubscribe(subscription);
        var count = policy == BackPressurePolicy.FULL ? 3 : (int) policy.getSegmentSize();

        for (var index = 0; index < count; index++) subscriber.onNext("item " + index);

        if (policy == BackPressurePolicy.FULL) {
            assertThat(subscription.requests).containsExactly(Long.MAX_VALUE);
        } else {
            assertThat(subscription.requests).containsExactly(policy.getSegmentSize(), policy.getSegmentSize());
        }
        assertThat(subscriber.items).hasSize(count).startsWith("item 0").endsWith("item " + (count - 1));
    }

    @Test
    void cancelBeforeSubscribeRejectsLateSubscription() {
        var subscriber = new RecordingSubscriber();
        var subscription = new RecordingSubscription();

        subscriber.cancel();
        subscriber.onSubscribe(subscription);

        assertThat(subscription.cancelCount).isEqualTo(1);
        assertThat(subscription.requests).isEmpty();
    }

    @Test
    void cancellationDropsLateItems() {
        var subscriber = new RecordingSubscriber();
        var subscription = new RecordingSubscription();
        subscriber.setBackPressurePolicy(BackPressurePolicy.MINIMUM);
        subscriber.onSubscribe(subscription);
        subscriber.onNext("before cancellation");

        subscriber.cancel();
        subscriber.cancel();
        subscriber.onNext("late item");

        assertThat(subscriber.items).containsExactly("before cancellation");
        assertThat(subscription.cancelCount).isEqualTo(1);
        assertThat(subscription.requests).containsExactly(1L, 1L);
    }

    private static final class RecordingSubscriber extends AbstractSubscriber<String> {
        private final List<String> items = new ArrayList<>();

        @Override
        protected void handle(String item) {
            items.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            throw new AssertionError(throwable);
        }

        @Override
        public void onComplete() {
        }
    }

    private static final class RecordingSubscription implements Flow.Subscription {
        private final List<Long> requests = new ArrayList<>();
        private int cancelCount;

        @Override
        public void request(long n) {
            requests.add(n);
        }

        @Override
        public void cancel() {
            cancelCount++;
        }
    }
}
