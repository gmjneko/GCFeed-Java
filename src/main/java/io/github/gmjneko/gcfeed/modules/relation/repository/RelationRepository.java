package io.github.gmjneko.gcfeed.modules.relation.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursor;
import io.github.gmjneko.gcfeed.modules.relation.entity.UserFollow;
import io.github.gmjneko.gcfeed.modules.relation.mapper.UserFollowMapper;
import io.github.gmjneko.gcfeed.modules.relation.vo.RelationUserVO;
import io.github.gmjneko.gcfeed.modules.user.entity.*;
import io.github.gmjneko.gcfeed.modules.user.mapper.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class RelationRepository {
    private final UserFollowMapper follows;
    private final UserMapper users;
    private final UserRelationStatMapper stats;

    public RelationRepository(UserFollowMapper follows, UserMapper users, UserRelationStatMapper stats) {
        this.follows = follows;
        this.users = users;
        this.stats = stats;
    }

    public void lockUsers(Long userId, Long targetId) {
        // 每条查询只锁一个主键，不依赖优化器的多行锁顺序。
        User first = lockUser(Math.min(userId, targetId));
        User second = lockUser(Math.max(userId, targetId));
        User actor = userId < targetId ? first : second;
        User target = userId < targetId ? second : first;
        if (target == null || target.getStatus() != 1)
            throw new BusinessException(ErrorCode.TARGET_USER_NOT_FOUND);
        if (actor == null || actor.getStatus() != 1)
            throw new BusinessException(ErrorCode.USER_STATUS_ABNORMAL);
        lockStat(Math.min(userId, targetId));
        lockStat(Math.max(userId, targetId));
    }

    private User lockUser(Long id) {
        return users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getId, id).last("FOR UPDATE"));
    }

    private void lockStat(Long id) {
        UserRelationStat stat = stats.selectOne(Wrappers.<UserRelationStat>lambdaQuery()
                .eq(UserRelationStat::getUserId, id).last("FOR UPDATE"));
        if (stat == null) {
            // 正常注册会初始化统计；兼容旧数据缺行时，按已有事实重建，而不是覆盖为零。
            stats.insert(UserRelationStat.builder().userId(id)
                    .followingCount(Math.toIntExact(follows.selectCount(Wrappers.<UserFollow>lambdaQuery()
                            .eq(UserFollow::getUserId, id).eq(UserFollow::getStatus, 1))))
                    .followerCount(Math.toIntExact(follows.selectCount(Wrappers.<UserFollow>lambdaQuery()
                            .eq(UserFollow::getTargetUserId, id).eq(UserFollow::getStatus, 1)))).build());
        }
    }

    public UserFollow find(Long userId, Long targetId) {
        return follows.selectOne(Wrappers.<UserFollow>lambdaQuery().eq(UserFollow::getUserId, userId)
                .eq(UserFollow::getTargetUserId, targetId));
    }

    public void insert(UserFollow relation) {
        follows.insert(relation);
    }

    public void setStatus(Long id, int status) {
        follows.update(Wrappers.<UserFollow>lambdaUpdate().eq(UserFollow::getId, id)
                .set(UserFollow::getStatus, status).set(UserFollow::getUpdatedAt, LocalDateTime.now()));
    }

    public void changeCounts(Long userId, Long targetId, int delta) {
        stats.update(Wrappers.<UserRelationStat>lambdaUpdate().eq(UserRelationStat::getUserId, userId)
                .setSql("following_count = GREATEST(following_count + {0}, 0)", delta)
                .set(UserRelationStat::getUpdatedAt, LocalDateTime.now()));
        stats.update(Wrappers.<UserRelationStat>lambdaUpdate().eq(UserRelationStat::getUserId, targetId)
                .setSql("follower_count = GREATEST(follower_count + {0}, 0)", delta)
                .set(UserRelationStat::getUpdatedAt, LocalDateTime.now()));
    }

    public UserRelationStat stat(Long id) {
        return stats.selectById(id);
    }

    public int followerCount(Long authorId) {
        UserRelationStat stat = stats.selectById(authorId);
        return stat == null || stat.getFollowerCount() == null ? 0 : stat.getFollowerCount();
    }

    public List<Long> followerIdsAfter(Long authorId, long afterUserId, int limit) {
        return follows.selectList(Wrappers.<UserFollow>lambdaQuery()
                        .select(UserFollow::getUserId)
                        .eq(UserFollow::getTargetUserId, authorId)
                        .eq(UserFollow::getStatus, 1)
                        .gt(UserFollow::getUserId, afterUserId)
                        .orderByAsc(UserFollow::getUserId)
                        .last("LIMIT " + limit))
                .stream().map(UserFollow::getUserId).toList();
    }

    public List<Long> bigVAuthorIds(Long viewerId, int threshold) {
        return follows.bigVAuthors(viewerId, threshold);
    }

    public boolean following(Long userId, Long targetId) {
        UserFollow relation = find(userId, targetId);
        return relation != null && relation.getStatus() != null && relation.getStatus() == 1;
    }

    public List<RelationUserVO> page(Long userId, boolean outgoing, TimeIdCursor cursor, int fetchLimit) {
        return outgoing ? follows.following(userId, cursor, fetchLimit) : follows.followers(userId, cursor, fetchLimit);
    }
}
