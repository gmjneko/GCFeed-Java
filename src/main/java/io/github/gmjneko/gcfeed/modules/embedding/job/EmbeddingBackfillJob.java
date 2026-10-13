package io.github.gmjneko.gcfeed.modules.embedding.job;

import io.github.gmjneko.gcfeed.modules.embedding.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时为缺少向量的上线视频补生成向量。
 *
 * <p>兜住三类缺口：发布事件丢失、单次生成失败、以及功能上线前已经存在的历史视频。
 * 事件链路正常时这个任务扫不到任何数据，开销只是一条带索引的查询。
 *
 * <p>循环分批处理而不是一次扫全表：历史数据可能很多，一次跑完会长时间占用连接和数据库
 * CPU。每处理完一批就重新查一次，是因为已经补上的视频不会再出现在结果里，天然向前推进。
 */
@Component
public class EmbeddingBackfillJob {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingBackfillJob.class);

    private final EmbeddingService embeddingService;
    private final int batchSize;
    private final int maxBatches;

    public EmbeddingBackfillJob(EmbeddingService embeddingService,
                                @Value("${gcfeed.embedding.backfill-batch-size:200}") int batchSize,
                                @Value("${gcfeed.embedding.backfill-max-batches:20}") int maxBatches) {
        this.embeddingService = embeddingService;
        this.batchSize = batchSize;
        this.maxBatches = maxBatches;
    }

    @Scheduled(fixedDelayString = "${gcfeed.embedding.backfill-interval-ms:600000}")
    public void backfill() {
        int total = 0;
        // 上限用于约束单次任务的运行时长；剩余部分下一轮继续，不会漏掉。
        for (int batch = 0; batch < maxBatches; batch++) {
            int generated;
            try {
                generated = embeddingService.backfillMissing(batchSize);
            } catch (RuntimeException ex) {
                log.warn("向量补偿任务中断，等待下一轮重试", ex);
                break;
            }
            total += generated;
            if (generated < batchSize) {
                break;
            }
        }
        if (total > 0) {
            log.info("向量补偿完成，本次生成 {} 个视频向量", total);
        }
    }
}
