package io.github.gmjneko.gcfeed.modules.interaction.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursor;
import io.github.gmjneko.gcfeed.modules.interaction.entity.InteractionComment;
import io.github.gmjneko.gcfeed.modules.interaction.mapper.InteractionCommentMapper;
import io.github.gmjneko.gcfeed.modules.user.entity.User;
import io.github.gmjneko.gcfeed.modules.user.mapper.UserMapper;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import io.github.gmjneko.gcfeed.modules.video.mapper.VideoStatMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Repository
public class CommentRepository {
    private final InteractionCommentMapper comments;
    private final UserMapper users;
    private final VideoStatMapper stats;

    public CommentRepository(InteractionCommentMapper comments, UserMapper users, VideoStatMapper stats) {
        this.comments = comments;
        this.users = users;
        this.stats = stats;
    }

    /**
     * 同一用户的跨视频请求键也必须串行，不能只锁当前视频。
     */
    public void lockAuthor(Long userId) {
        User user = users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getId, userId).last("FOR UPDATE"));
        if (user == null || user.getStatus() != 1) throw new BusinessException(ErrorCode.USER_STATUS_ABNORMAL);
    }

    public InteractionComment byKey(Long userId, String key) {
        return comments.selectOne(Wrappers.<InteractionComment>lambdaQuery()
                .eq(InteractionComment::getUserId, userId).eq(InteractionComment::getIdempotencyKey, key)
                .last("FOR UPDATE"));
    }

    public InteractionComment find(Long id, boolean lock) {
        var query = Wrappers.<InteractionComment>lambdaQuery().eq(InteractionComment::getId, id);
        if (lock) query.last("FOR UPDATE");
        return comments.selectOne(query);
    }

    public void insert(InteractionComment comment) {
        comments.insert(comment);
    }

    public boolean delete(Long id) {
        return comments.update(Wrappers.<InteractionComment>lambdaUpdate()
                .eq(InteractionComment::getId, id).eq(InteractionComment::getStatus, 1)
                .set(InteractionComment::getStatus, 2).set(InteractionComment::getUpdatedAt, LocalDateTime.now())) == 1;
    }

    public void changeCount(Long videoId, int delta) {
        int changed = stats.update(Wrappers.<VideoStat>lambdaUpdate().eq(VideoStat::getVideoId, videoId)
                .setSql("comment_count = GREATEST(comment_count + {0}, 0)", delta)
                .set(VideoStat::getUpdatedAt, LocalDateTime.now()));
        if (changed != 1) throw new IllegalStateException("video stat is missing: " + videoId);
    }

    public int count(Long videoId) {
        VideoStat stat = stats.selectById(videoId);
        return stat == null || stat.getCommentCount() == null ? 0 : Math.max(0, stat.getCommentCount());
    }

    public List<InteractionComment> page(Long videoId, TimeIdCursor cursor, int fetchLimit) {
        var query = Wrappers.<InteractionComment>lambdaQuery()
                .eq(InteractionComment::getVideoId, videoId).eq(InteractionComment::getStatus, 1)
                .orderByDesc(InteractionComment::getCreatedAt, InteractionComment::getId);
        if (cursor != null) query.and(q -> q.lt(InteractionComment::getCreatedAt, cursor.time())
                .or(t -> t.eq(InteractionComment::getCreatedAt, cursor.time()).lt(InteractionComment::getId, cursor.id())));
        return comments.selectPage(new Page<>(1, fetchLimit, false), query).getRecords();
    }

    public Map<Long, User> authors(List<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return users.selectList(Wrappers.<User>lambdaQuery().select(User::getId, User::getNickname, User::getAvatarUrl)
                        .in(User::getId, ids).ne(User::getStatus, 3)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }
}
