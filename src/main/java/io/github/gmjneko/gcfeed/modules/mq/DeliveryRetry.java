package io.github.gmjneko.gcfeed.modules.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 同一条消息失败达到上限后写入死信并停止重入，避免毒消息打满队列。 */
@Component
public class DeliveryRetry {

    private static final Logger log = LoggerFactory.getLogger(DeliveryRetry.class);

    private final EventReceipts receipts;
    private final DeadLetterStore deadLetters;
    private final RabbitSettings settings;
    private final Delay delay;

    @Autowired
    public DeliveryRetry(EventReceipts receipts, DeadLetterStore deadLetters, RabbitSettings settings) {
        this(receipts, deadLetters, settings, attempt -> {
            try {
                Thread.sleep(1000L * attempt);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        });
    }

    DeliveryRetry(EventReceipts receipts, DeadLetterStore deadLetters, RabbitSettings settings, Delay delay) {
        this.receipts = receipts;
        this.deadLetters = deadLetters;
        this.settings = settings;
        this.delay = delay;
    }

    public void run(String consumer, String eventId, String taskType, String payload, Runnable action) {
        if (eventId == null || eventId.isBlank() || receipts.done(consumer, eventId)) {
            return;
        }
        try {
            action.run();
            receipts.markDone(consumer, eventId);
        } catch (RuntimeException ex) {
            int attempt = receipts.nextAttempt(consumer, eventId);
            if (attempt >= settings.maxAttempts()) {
                deadLetters.record(taskType, payload, attempt, ex.getMessage());
                receipts.markDone(consumer, eventId);
                log.warn("消息超过重试次数，已写入死信 consumer={} eventId={} attempt={}", consumer, eventId, attempt);
                return;
            }
            delay.wait(attempt);
            throw ex;
        }
    }

    @FunctionalInterface
    interface Delay {
        void wait(int attempt);
    }
}
