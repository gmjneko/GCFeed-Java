package io.github.gmjneko.gcfeed.modules.relation.service;

import io.github.gmjneko.gcfeed.common.annotation.Idempotent;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.pagination.*;
import io.github.gmjneko.gcfeed.modules.relation.entity.UserFollow;
import io.github.gmjneko.gcfeed.modules.relation.repository.RelationRepository;
import io.github.gmjneko.gcfeed.modules.relation.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.List;

@Service
public class RelationService {
    private final RelationRepository repository;

    public RelationService(RelationRepository repository) {
        this.repository = repository;
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
            // TODO(RabbitMQ): 在同一事务记录 Outbox，提交后可靠发布 relation.followed/unfollowed。
            // TODO(RabbitMQ): 关注通知及历史作品回填/取关清理收件箱；消费者幂等、重试、死信，禁止提交前发送。
        }
        return new FollowVO(userId, targetId, active ? 1 : 2, active,
                repository.stat(userId).getFollowingCount(), repository.stat(targetId).getFollowerCount());
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
