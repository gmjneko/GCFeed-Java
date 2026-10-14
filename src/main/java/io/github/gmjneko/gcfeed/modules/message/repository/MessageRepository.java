package io.github.gmjneko.gcfeed.modules.message.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursor;
import io.github.gmjneko.gcfeed.modules.message.entity.UserMessage;
import io.github.gmjneko.gcfeed.modules.message.mapper.UserMessageMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public class MessageRepository {

    private final UserMessageMapper messages;

    public MessageRepository(UserMessageMapper messages) {
        this.messages = messages;
    }

    /** 插入新消息。唯一键冲突时返回已有行，并标明这次不是新建。 */
    public InsertResult insert(UserMessage message) {
        try {
            messages.insert(message);
            return new InsertResult(message, true);
        } catch (DuplicateKeyException ex) {
            UserMessage existing = findExisting(message.getUserId(), message.getEventId(), message.getIdempotencyKey());
            if (existing == null) {
                throw new BusinessException(ErrorCode.SAVE_MESSAGE_FAILED, ex);
            }
            return new InsertResult(existing, false);
        }
    }

    public List<UserMessage> page(Long userId, TimeIdCursor cursor, int fetchLimit) {
        var query = Wrappers.<UserMessage>lambdaQuery()
                .eq(UserMessage::getUserId, userId)
                .orderByDesc(UserMessage::getCreatedAt, UserMessage::getId);
        if (cursor != null) {
            query.and(nested -> nested.lt(UserMessage::getCreatedAt, cursor.time())
                    .or(sameTime -> sameTime.eq(UserMessage::getCreatedAt, cursor.time())
                            .lt(UserMessage::getId, cursor.id())));
        }
        return messages.selectPage(new Page<>(1, fetchLimit, false), query).getRecords();
    }

    public int countUnread(Long userId) {
        Long count = messages.selectCount(Wrappers.<UserMessage>lambdaQuery()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0));
        return count == null ? 0 : Math.toIntExact(count);
    }

    /** 只更新当前用户仍未读的行。空 ID 集合表示全部未读。 */
    public int markRead(Long userId, Collection<Long> messageIds, LocalDateTime readAt) {
        var update = Wrappers.<UserMessage>lambdaUpdate()
                .eq(UserMessage::getUserId, userId)
                .eq(UserMessage::getIsRead, 0)
                .set(UserMessage::getIsRead, 1)
                .set(UserMessage::getReadAt, readAt);
        if (messageIds != null && !messageIds.isEmpty()) {
            update.in(UserMessage::getId, messageIds);
        }
        return messages.update(null, update);
    }

    private UserMessage findExisting(Long userId, String eventId, String idempotencyKey) {
        if (eventId == null && idempotencyKey == null) {
            return null;
        }
        var query = Wrappers.<UserMessage>lambdaQuery().eq(UserMessage::getUserId, userId);
        if (eventId != null && idempotencyKey != null) {
            query.and(nested -> nested.eq(UserMessage::getEventId, eventId)
                    .or()
                    .eq(UserMessage::getIdempotencyKey, idempotencyKey));
        } else if (eventId != null) {
            query.eq(UserMessage::getEventId, eventId);
        } else {
            query.eq(UserMessage::getIdempotencyKey, idempotencyKey);
        }
        return messages.selectOne(query.orderByDesc(UserMessage::getId).last("LIMIT 1"));
    }

    public record InsertResult(UserMessage message, boolean created) {
    }
}
