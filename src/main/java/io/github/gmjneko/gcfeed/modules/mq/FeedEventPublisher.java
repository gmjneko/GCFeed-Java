package io.github.gmjneko.gcfeed.modules.mq;

import io.github.gmjneko.gcfeed.modules.embedding.service.VideoPublishedEvent;

/** 发布已经提交的领域事件。关闭 MQ 时只在进程内分发，启用时写入 Outbox。 */
public interface FeedEventPublisher {

    void videoPublished(VideoPublishedEvent event);

    void relationChanged(RelationChangedEvent event);
}
