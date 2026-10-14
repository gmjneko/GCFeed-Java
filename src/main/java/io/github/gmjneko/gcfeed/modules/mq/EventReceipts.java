package io.github.gmjneko.gcfeed.modules.mq;

/** 消费端去重和失败次数。成功或进入死信后再次投递不再执行。 */
public interface EventReceipts {

    boolean done(String consumer, String eventId);

    int nextAttempt(String consumer, String eventId);

    void markDone(String consumer, String eventId);
}
