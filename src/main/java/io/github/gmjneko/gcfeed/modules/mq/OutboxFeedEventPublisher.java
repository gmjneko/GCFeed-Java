package io.github.gmjneko.gcfeed.modules.mq;

import io.github.gmjneko.gcfeed.modules.embedding.service.VideoPublishedEvent;
import io.github.gmjneko.gcfeed.modules.mq.entity.EventOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.TimeUnit;

/** 业务事务内只写 Outbox。提交后发送；确认失败留给定时任务补发，不让发布接口失败。 */
@Component
@ConditionalOnProperty(name = "gcfeed.rabbitmq.enabled", havingValue = "true")
public class OutboxFeedEventPublisher implements FeedEventPublisher {

    public static final String VIDEO_PUBLISHED = "video.published";
    public static final String RELATION_CHANGED = "relation.changed";

    private static final Logger log = LoggerFactory.getLogger(OutboxFeedEventPublisher.class);

    private final OutboxRepository outbox;
    private final RabbitTemplate rabbit;
    private final JsonMapper json;

    public OutboxFeedEventPublisher(OutboxRepository outbox, RabbitTemplate rabbit, JsonMapper json) {
        this.outbox = outbox;
        this.rabbit = rabbit;
        this.json = json;
        rabbit.setMandatory(true);
    }

    @Override
    public void videoPublished(VideoPublishedEvent event) {
        enqueue(event.eventId(), VIDEO_PUBLISHED, json.writeValueAsString(event));
    }

    @Override
    public void relationChanged(RelationChangedEvent event) {
        enqueue(event.eventId(), RELATION_CHANGED, json.writeValueAsString(event));
    }

    @Scheduled(fixedDelayString = "${gcfeed.rabbitmq.relay-interval-ms:5000}")
    public void relay() {
        publishPending();
    }

    public void publishPending() {
        for (EventOutbox row : outbox.pending(100)) {
            publish(row);
        }
    }

    private void enqueue(String eventId, String type, String payload) {
        outbox.insertPending(eventId, type, payload);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    publishPending();
                }
            });
        } else {
            publishPending();
        }
    }

    private void publish(EventOutbox row) {
        try {
            String exchange = RELATION_CHANGED.equals(row.getEventType())
                    ? RabbitTopology.RELATION_EXCHANGE : RabbitTopology.VIDEO_EXCHANGE;
            String routingKey = RELATION_CHANGED.equals(row.getEventType())
                    ? RabbitTopology.RELATION_CHANGED : RabbitTopology.VIDEO_PUBLISHED;
            CorrelationData confirmation = new CorrelationData(row.getEventId());
            rabbit.convertAndSend(exchange, routingKey, row.getPayload(), message -> {
                message.getMessageProperties().setContentType("application/json");
                message.getMessageProperties().setMessageId(row.getEventId());
                return message;
            }, confirmation);
            CorrelationData.Confirm confirm = confirmation.getFuture().get(5, TimeUnit.SECONDS);
            if (confirm == null || !confirm.isAck() || confirmation.getReturned() != null) {
                throw new IllegalStateException("rabbit publish was not confirmed");
            }
            outbox.markSent(row.getId());
        } catch (Exception ex) {
            log.warn("Outbox 发送失败，等待补发 eventId={}: {}", row.getEventId(), ex.toString());
        }
    }
}
