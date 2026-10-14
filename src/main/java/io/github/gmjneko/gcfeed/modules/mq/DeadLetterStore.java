package io.github.gmjneko.gcfeed.modules.mq;

/** 超过重试次数后保存原始消息，避免毒消息反复入队。 */
public interface DeadLetterStore {

    void record(String taskType, String payload, int retryCount, String lastError);
}
