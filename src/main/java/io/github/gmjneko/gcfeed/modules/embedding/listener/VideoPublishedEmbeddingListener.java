package io.github.gmjneko.gcfeed.modules.embedding.listener;

import io.github.gmjneko.gcfeed.modules.embedding.service.EmbeddingService;
import io.github.gmjneko.gcfeed.modules.embedding.service.VideoPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 消费视频发布事件生成向量。
 *
 * <p>挂在 {@link TransactionPhase#AFTER_COMMIT}：视频行提交后才生成向量。如果提前到事务内，
 * 事务回滚会留下一份指向不存在视频的向量；而且生成过程（换真实模型后是一次网络调用）
 * 会拉长数据库事务持有时间。
 *
 * <p>{@code fallbackExecution = true} 让没有事务时也能执行：单条写入场景和测试直接调用
 * Service 时没有事务上下文，此时没有「未提交」的风险，正常处理更符合直觉。
 *
 * <p>接入 RabbitMQ 后，这个类改成消息监听器即可——事件类型和 Service 都不动。
 */
@Component
@ConditionalOnProperty(name = "gcfeed.rabbitmq.enabled", havingValue = "false", matchIfMissing = true)
public class VideoPublishedEmbeddingListener {

    private static final Logger log = LoggerFactory.getLogger(VideoPublishedEmbeddingListener.class);

    private final EmbeddingService embeddingService;

    public VideoPublishedEmbeddingListener(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onVideoPublished(VideoPublishedEvent event) {
        try {
            embeddingService.generateForPublishedVideo(event);
        } catch (RuntimeException ex) {
            // 向量是派生数据：生成失败不能让已经成功的发布操作对外表现成失败。
            // 补偿任务会重新选中这个视频，这里只记录便于发现链路整体故障。
            log.warn("视频向量生成失败，等待补偿任务重试 videoId={}",
                    event == null ? null : event.videoId(), ex);
        }
    }
}
