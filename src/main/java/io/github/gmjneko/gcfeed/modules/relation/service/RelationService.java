package io.github.gmjneko.gcfeed.modules.relation.service;

import io.github.gmjneko.gcfeed.common.annotation.Idempotent;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.pagination.*;
import io.github.gmjneko.gcfeed.modules.mq.FeedEventPublisher;
import io.github.gmjneko.gcfeed.modules.mq.RelationChangedEvent;
import io.github.gmjneko.gcfeed.modules.message.model.MessageDraft;
import io.github.gmjneko.gcfeed.modules.message.model.MessageType;
import io.github.gmjneko.gcfeed.modules.message.service.MessageNotifier;
import io.github.gmjneko.gcfeed.modules.relation.entity.UserFollow;
import io.github.gmjneko.gcfeed.modules.relation.repository.RelationRepository;
import io.github.gmjneko.gcfeed.modules.relation.vo.*;
import io.github.gmjneko.gcfeed.modules.user.service.UserService;
import io.github.gmjneko.gcfeed.modules.user.vo.ActorSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RelationService {
    private final RelationRepository repository;
    private final UserService users;
    private final MessageNotifier messages;
    private final FeedEventPublisher events;

    public RelationService(RelationRepository repository, UserService users, MessageNotifier messages,
                           FeedEventPublisher events) {
        this.repository = repository;
        this.users = users;
        this.messages = messages;
        this.events = events;
    }

    @Idempotent(scope = "relation.follow", userId = "#userId", key = "#key")
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FollowVO follow(Long userId, Long targetId, String key) {
        return set(userId, targetId, true);
    }

    @Idempotent(scope = "relation.unfollow", userId = "#userId", key = "#key")
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public FollowVO unfollow(Long userId, Long targetId, String key) {
        return set(userId, targetId, false);
    }

    private FollowVO set(Long userId, Long targetId, boolean active) {
        if (targetId == null || targetId <= 0) throw new BusinessException(ErrorCode.INVALID_TARGET_USER_ID);
        if (targetId.equals(userId)) throw new BusinessException(ErrorCode.FOLLOW_SELF_FORBIDDEN);
        repository.lockUsers(userId, targetId);
        UserFollow relation = repository.find(userId, targetId);
        boolean wasActive = relation != null && relation.getStatus() == 1;
        if (wasActive != active) {
            if (relation == null) {
                relation = new UserFollow();
                relation.setUserId(userId);
                relation.setTargetUserId(targetId);
                relation.setStatus(1);
                repository.insert(relation);
            } else repository.setStatus(relation.getId(), active ? 1 : 2);
            repository.changeCounts(userId, targetId, active ? 1 : -1);
            if (active) {
                notifyFollow(userId, targetId);
            }
            events.relationChanged(new RelationChangedEvent(
                    UUID.randomUUID().toString(),
                    active ? RelationChangedEvent.FOLLOW : RelationChangedEvent.UNFOLLOW,
                    userId, targetId, Instant.now()));
        }
        return new FollowVO(userId, targetId, active ? 1 : 2, active,
                repository.stat(userId).getFollowingCount(), repository.stat(targetId).getFollowerCount());
    }

    private void notifyFollow(Long userId, Long targetId) {
        ActorSnapshot actor = users.findActor(userId).orElse(null);
        messages.notifyAfterCommit(MessageDraft.notification(
                targetId,
                MessageType.FOLLOW.name(),
                "新增关注",
                "关注了你",
                "relation:follow:" + targetId + ":" + userId,
                userId,
                actor == null ? null : actor.nickname(),
                actor == null ? null : actor.avatarUrl()));
    }

    public CursorPage<RelationUserVO> list(Long userId, boolean outgoing, String cursor, int limit) {
        if (limit <= 0) throw new BusinessException(ErrorCode.INVALID_LIMIT);
        int size = Math.min(limit, 100);
        List<RelationUserVO> rows = repository.page(userId, outgoing, TimeIdCursorCodec.decode(cursor), size + 1);
        boolean more = rows.size() > size;
        var page = more ? rows.subList(0, size) : rows;
        String next = page.isEmpty() ? "" : TimeIdCursorCodec.encode(new TimeIdCursor(
                page.getLast().followedAt(), page.getLast().userId()));
        return new CursorPage<>(page, next, more);
    }
}
