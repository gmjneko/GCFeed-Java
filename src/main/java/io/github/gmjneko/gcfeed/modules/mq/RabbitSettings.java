package io.github.gmjneko.gcfeed.modules.mq;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 关注流分发使用的 RabbitMQ 开关和推拉参数。连接地址在 spring.rabbitmq 下。 */
@ConfigurationProperties(prefix = "gcfeed.rabbitmq")
public record RabbitSettings(
        boolean enabled,
        long relayIntervalMs,
        int bigVFollowerThreshold,
        int followerBatchSize,
        int inboxMaxLen,
        int outboxMaxLen,
        int backfillLimit,
        int maxAttempts
) {
    public RabbitSettings {
        if (relayIntervalMs <= 0) {
            relayIntervalMs = 5000;
        }
        if (bigVFollowerThreshold <= 0) {
            bigVFollowerThreshold = 10000;
        }
        if (followerBatchSize <= 0) {
            followerBatchSize = 500;
        }
        if (inboxMaxLen <= 0) {
            inboxMaxLen = 1000;
        }
        if (outboxMaxLen <= 0) {
            outboxMaxLen = 500;
        }
        if (backfillLimit <= 0) {
            backfillLimit = 100;
        }
        if (maxAttempts <= 0) {
            maxAttempts = 3;
        }
    }
}
