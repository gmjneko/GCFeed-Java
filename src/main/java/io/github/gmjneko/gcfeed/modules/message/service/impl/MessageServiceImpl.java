package io.github.gmjneko.gcfeed.modules.message.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.pagination.CursorPage;
import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursor;
import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursorCodec;
import io.github.gmjneko.gcfeed.modules.message.cache.MessageUnreadCache;
import io.github.gmjneko.gcfeed.modules.message.entity.UserMessage;
import io.github.gmjneko.gcfeed.modules.message.model.MessageDraft;
import io.github.gmjneko.gcfeed.modules.message.model.MessageType;
import io.github.gmjneko.gcfeed.modules.message.repository.MessageRepository;
import io.github.gmjneko.gcfeed.modules.message.service.MessageService;
import io.github.gmjneko.gcfeed.modules.message.vo.MarkReadVO;
import io.github.gmjneko.gcfeed.modules.message.vo.MessageVO;
import io.github.gmjneko.gcfeed.modules.message.vo.UnreadStatVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;

@Service
public class MessageServiceImpl implements MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageServiceImpl.class);

    private final MessageRepository repository;
    private final MessageUnreadCache unreadCache;

    public MessageServiceImpl(MessageRepository repository, MessageUnreadCache unreadCache) {
        this.repository = repository;
        this.unreadCache = unreadCache;
    }

    @Override
    public CursorPage<MessageVO> list(Long userId, String cursor, int limit) {
        requireUser(userId);
        if (limit <= 0) {
            throw new BusinessException(ErrorCode.INVALID_LIMIT);
        }
        TimeIdCursor position = TimeIdCursorCodec.decode(cursor);
        int size = Math.min(limit, CommonConstant.MAX_PAGE_SIZE);
        List<UserMessage> rows;
        try {
            rows = repository.page(userId, position, size + 1);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.LOAD_MESSAGE_FAILED, ex);
        }
        boolean more = rows.size() > size;
        List<UserMessage> page = more ? rows.subList(0, size) : rows;
        String next = page.isEmpty() ? "" : TimeIdCursorCodec.encode(
                new TimeIdCursor(page.getLast().getCreatedAt(), page.getLast().getId()));
        return new CursorPage<>(page.stream().map(MessageVO::from).toList(), next, more);
    }

    @Override
    public UnreadStatVO unread(Long userId) {
        requireUser(userId);
        try {
            OptionalInt cached = unreadCache.get(userId);
            if (cached.isPresent()) {
                return new UnreadStatVO(cached.getAsInt());
            }
        } catch (RuntimeException ex) {
            log.warn("读取未读缓存失败，改为查询数据库 userId={}", userId, ex);
            return new UnreadStatVO(countUnread(userId));
        }
        int count = countUnread(userId);
        try {
            unreadCache.put(userId, count);
        } catch (RuntimeException ex) {
            log.warn("写入未读缓存失败 userId={}", userId, ex);
        }
        return new UnreadStatVO(count);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarkReadVO markRead(Long userId, List<Long> messageIds) {
        requireUser(userId);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (messageIds != null) {
            for (Long id : messageIds) {
                if (id == null || id <= 0) {
                    throw new BusinessException(ErrorCode.INVALID_MESSAGE_ID);
                }
                ids.add(id);
            }
        }
        int updated;
        try {
            updated = repository.markRead(userId, ids, LocalDateTime.now());
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.UPDATE_MESSAGE_FAILED, ex);
        }
        evictUnread(userId);
        return new MarkReadVO(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateResult create(MessageDraft draft) {
        if (draft == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        UserMessage message = normalize(draft);
        MessageRepository.InsertResult saved;
        try {
            saved = repository.insert(message);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.SAVE_MESSAGE_FAILED, ex);
        }
        evictUnread(saved.message().getUserId());
        return new CreateResult(MessageVO.from(saved.message()), saved.created());
    }

    private int countUnread(Long userId) {
        try {
            return repository.countUnread(userId);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.LOAD_MESSAGE_FAILED, ex);
        }
    }

    /** 缓存删除失败不能回滚已经成功的消息写入或已读更新。 */
    private void evictUnread(Long userId) {
        try {
            unreadCache.evict(userId);
        } catch (RuntimeException ex) {
            log.warn("清除未读缓存失败 userId={}", userId, ex);
        }
    }

    private UserMessage normalize(MessageDraft draft) {
        if (draft.userId() == null || draft.userId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_USER_ID);
        }
        UserMessage message = new UserMessage();
        message.setUserId(draft.userId());
        message.setType(MessageType.normalize(draft.type()).name());
        message.setTitle(required(draft.title(), ErrorCode.EMPTY_MESSAGE_TITLE, ErrorCode.MESSAGE_TITLE_TOO_LONG,
                CommonConstant.MAX_MESSAGE_TITLE_LENGTH));
        message.setContent(required(draft.content(), ErrorCode.EMPTY_MESSAGE_CONTENT, ErrorCode.MESSAGE_CONTENT_TOO_LONG,
                CommonConstant.MAX_MESSAGE_CONTENT_LENGTH));
        // 空串必须落成 NULL，否则唯一索引会把所有空事件当成同一条。
        message.setEventId(optional(draft.eventId(), ErrorCode.EVENT_ID_TOO_LONG, CommonConstant.MAX_MESSAGE_EVENT_ID_LENGTH));
        message.setIdempotencyKey(optional(draft.idempotencyKey(), ErrorCode.MESSAGE_IDEMPOTENCY_KEY_TOO_LONG,
                CommonConstant.MAX_IDEMPOTENCY_KEY_LENGTH));
        message.setActorId(draft.actorId() != null && draft.actorId() > 0 ? draft.actorId() : 0L);
        message.setActorNickname(optional(draft.actorNickname(), ErrorCode.ACTOR_NICKNAME_TOO_LONG,
                CommonConstant.MAX_MESSAGE_ACTOR_NICKNAME_LENGTH));
        message.setActorAvatarUrl(optional(draft.actorAvatarUrl(), ErrorCode.ACTOR_AVATAR_URL_TOO_LONG,
                CommonConstant.MAX_AVATAR_URL_LENGTH));
        message.setIsRead(0);
        message.setDeleted(0);
        return message;
    }

    private void requireUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private static String required(String value, ErrorCode empty, ErrorCode tooLong, int max) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new BusinessException(empty);
        }
        if (text.codePointCount(0, text.length()) > max) {
            throw new BusinessException(tooLong);
        }
        return text;
    }

    private static String optional(String value, ErrorCode tooLong, int max) {
        if (value == null) {
            return null;
        }
        String text = value.strip();
        if (text.isEmpty()) {
            return null;
        }
        if (text.codePointCount(0, text.length()) > max) {
            throw new BusinessException(tooLong);
        }
        return text;
    }
}
