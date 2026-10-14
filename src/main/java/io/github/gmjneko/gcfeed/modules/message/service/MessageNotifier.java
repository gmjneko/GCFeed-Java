package io.github.gmjneko.gcfeed.modules.message.service;

import io.github.gmjneko.gcfeed.modules.message.model.MessageDraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 在主事务提交后写通知。失败只记日志，不能把已经成功的点赞、评论或关注变成错误响应。
 */
@Component
public class MessageNotifier {

    private static final Logger log = LoggerFactory.getLogger(MessageNotifier.class);

    private final MessageService messages;

    public MessageNotifier(MessageService messages) {
        this.messages = messages;
    }

    public void notifyAfterCommit(MessageDraft draft) {
        if (draft == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeCreate(draft);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeCreate(draft);
            }
        });
    }

    private void safeCreate(MessageDraft draft) {
        try {
            messages.create(draft);
        } catch (RuntimeException ex) {
            log.warn("站内通知写入失败 userId={} eventId={}", draft.userId(), draft.eventId(), ex);
        }
    }
}
