package io.github.gmjneko.gcfeed.modules.embedding.service;

import java.time.Instant;

/**
 * 视频发布事件。
 *
 * <p>单独定义事件类型，而不是把 {@code video} 实体传给消费者：向量化只关心标题和简介，
 * 拿到整个实体既可能误用其他字段，也让 embedding 模块反向依赖 video 模块的内部结构。
 *
 * <p>当前是进程内 Spring 事件；接入 RabbitMQ 后同一个类型就是消息体，消费者代码不用改。
 *
 * @param eventId     事件唯一标识，供消费端幂等去重
 * @param videoId     视频 ID
 * @param authorId    作者 ID
 * @param title       标题
 * @param description 简介，可能为空
 * @param occurredAt  事件发生时间
 */
public record VideoPublishedEvent(
        String eventId,
        Long videoId,
        Long authorId,
        String title,
        String description,
        Instant occurredAt) {

    /** 判断事件是否可用于生成向量：只有视频 ID 和标题都有值才有意义。 */
    public boolean usable() {
        return videoId != null && videoId > 0 && title != null && !title.isBlank();
    }
}
