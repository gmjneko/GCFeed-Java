package io.github.gmjneko.gcfeed.modules.mq;

import io.github.gmjneko.gcfeed.modules.embedding.service.EmbeddingService;
import io.github.gmjneko.gcfeed.modules.embedding.service.VideoPublishedEvent;
import io.github.gmjneko.gcfeed.modules.feed.following.RelationIndexService;
import io.github.gmjneko.gcfeed.modules.feed.following.VideoFanoutService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 启用 MQ 时消费发布扇出、向量和关注关系。失败次数由 DeliveryRetry 控制。 */
@Component
@ConditionalOnProperty(name = "gcfeed.rabbitmq.enabled", havingValue = "true")
public class FeedMessageListener {

    private final JsonMapper json;
    private final DeliveryRetry retry;
    private final VideoFanoutService fanout;
    private final EmbeddingService embeddings;
    private final RelationIndexService relations;
    private final DeadLetterStore deadLetters;

    public FeedMessageListener(JsonMapper json, DeliveryRetry retry, VideoFanoutService fanout,
                               EmbeddingService embeddings, RelationIndexService relations,
                               DeadLetterStore deadLetters) {
        this.json = json;
        this.retry = retry;
        this.fanout = fanout;
        this.embeddings = embeddings;
        this.relations = relations;
        this.deadLetters = deadLetters;
    }

    @RabbitListener(queues = RabbitTopology.PUBLISHED_QUEUE)
    public void fanout(String body) {
        VideoPublishedEvent event = json.readValue(body, VideoPublishedEvent.class);
        retry.run("video-fanout", event.eventId(), OutboxFeedEventPublisher.VIDEO_PUBLISHED, body,
                () -> fanout.onPublished(event));
    }

    @RabbitListener(queues = RabbitTopology.EMBEDDING_QUEUE)
    public void embedding(String body) {
        VideoPublishedEvent event = json.readValue(body, VideoPublishedEvent.class);
        retry.run("video-embedding", event.eventId(), "video.embedding", body,
                () -> embeddings.generateForPublishedVideo(event));
    }

    @RabbitListener(queues = RabbitTopology.RELATION_QUEUE)
    public void relation(String body) {
        RelationChangedEvent event = json.readValue(body, RelationChangedEvent.class);
        retry.run("relation-index", event.eventId(), OutboxFeedEventPublisher.RELATION_CHANGED, body,
                () -> relations.onChanged(event));
    }

    @RabbitListener(queues = {
            RabbitTopology.PUBLISHED_QUEUE + ".dlq",
            RabbitTopology.EMBEDDING_QUEUE + ".dlq",
            RabbitTopology.RELATION_QUEUE + ".dlq"
    })
    public void dead(Message message) {
        String type = message.getMessageProperties().getConsumerQueue();
        deadLetters.record(type == null ? "dead-letter" : type,
                new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8),
                3, "rejected to dead letter queue");
    }
}
