package io.github.gmjneko.gcfeed.modules.mq;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryRetryTest {

    @Test
    void thirdFailureIsRecordedOnceAndLaterDeliveriesStop() {
        MemoryReceipts receipts = new MemoryReceipts();
        MemoryDeadLetters letters = new MemoryDeadLetters();
        DeliveryRetry retry = new DeliveryRetry(receipts, letters,
                new RabbitSettings(true, 5000, 10000, 500, 1000, 500, 100, 3), attempt -> {
        });
        Runnable fail = () -> {
            throw new IllegalStateException("boom");
        };
        assertThatThrownBy(() -> retry.run("fanout", "event-1", "video.published", "{}", fail))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> retry.run("fanout", "event-1", "video.published", "{}", fail))
                .isInstanceOf(IllegalStateException.class);
        retry.run("fanout", "event-1", "video.published", "{}", fail);
        retry.run("fanout", "event-1", "video.published", "{}", fail);
        assertThat(letters.rows).containsExactly("video.published");
    }

    private static final class MemoryReceipts implements EventReceipts {
        private final ConcurrentHashMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Boolean> done = new ConcurrentHashMap<>();

        @Override
        public boolean done(String consumer, String eventId) {
            return done.containsKey(consumer + ":" + eventId);
        }

        @Override
        public int nextAttempt(String consumer, String eventId) {
            return attempts.computeIfAbsent(consumer + ":" + eventId, ignored -> new AtomicInteger()).incrementAndGet();
        }

        @Override
        public void markDone(String consumer, String eventId) {
            done.put(consumer + ":" + eventId, Boolean.TRUE);
        }
    }

    private static final class MemoryDeadLetters implements DeadLetterStore {
        private final List<String> rows = new ArrayList<>();

        @Override
        public void record(String taskType, String payload, int retryCount, String lastError) {
            rows.add(taskType);
            assertThat(retryCount).isEqualTo(3);
            assertThat(lastError).isEqualTo("boom");
        }
    }
}
