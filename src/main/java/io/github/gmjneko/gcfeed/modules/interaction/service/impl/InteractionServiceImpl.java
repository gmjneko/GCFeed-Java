package io.github.gmjneko.gcfeed.modules.interaction.service.impl;

import io.github.gmjneko.gcfeed.common.annotation.Idempotent;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.feed.hot.HotRanking;
import io.github.gmjneko.gcfeed.modules.interaction.model.ActionType;
import io.github.gmjneko.gcfeed.modules.interaction.repository.InteractionRepository;
import io.github.gmjneko.gcfeed.modules.interaction.service.InteractionService;
import io.github.gmjneko.gcfeed.modules.interaction.vo.ActionVO;
import io.github.gmjneko.gcfeed.modules.message.model.MessageDraft;
import io.github.gmjneko.gcfeed.modules.message.model.MessageType;
import io.github.gmjneko.gcfeed.modules.message.service.MessageNotifier;
import io.github.gmjneko.gcfeed.modules.user.service.UserService;
import io.github.gmjneko.gcfeed.modules.user.vo.ActorSnapshot;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import io.github.gmjneko.gcfeed.modules.video.model.VideoStatField;
import io.github.gmjneko.gcfeed.modules.video.service.VideoStatDeltaBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * 互动状态强一致、计数最终一致。
 *
 * <p>interaction_action 是事实来源，在事务内同步翻转；video_stat 的计数只在事务提交后
 * 记入增量缓冲，由定时任务合并刷库，请求路径上不再锁视频行或计数行。
 */
@Service
@Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
public class InteractionServiceImpl implements InteractionService {
    private static final Logger log = LoggerFactory.getLogger(InteractionServiceImpl.class);

    private final InteractionRepository repository;
    private final VideoStatDeltaBuffer statBuffer;
    private final HotRanking hotRanking;
    private final UserService users;
    private final MessageNotifier messages;

    public InteractionServiceImpl(InteractionRepository repository, VideoStatDeltaBuffer statBuffer,
                                  HotRanking hotRanking, UserService users, MessageNotifier messages) {
        this.repository = repository;
        this.statBuffer = statBuffer;
        this.hotRanking = hotRanking;
        this.users = users;
        this.messages = messages;
    }

    @Override
    @Idempotent(scope = "interaction.like", userId = "#userId", key = "#key")
    public ActionVO like(Long userId, Long videoId, String key) {
        return setAction(userId, videoId, ActionType.LIKE, true);
    }

    @Override
    @Idempotent(scope = "interaction.unlike", userId = "#userId", key = "#key")
    public ActionVO unlike(Long userId, Long videoId, String key) {
        return setAction(userId, videoId, ActionType.LIKE, false);
    }

    @Override
    @Idempotent(scope = "interaction.favorite", userId = "#userId", key = "#key")
    public ActionVO favorite(Long userId, Long videoId, String key) {
        return setAction(userId, videoId, ActionType.FAVORITE, true);
    }

    @Override
    @Idempotent(scope = "interaction.unfavorite", userId = "#userId", key = "#key")
    public ActionVO unfavorite(Long userId, Long videoId, String key) {
        return setAction(userId, videoId, ActionType.FAVORITE, false);
    }

    private ActionVO setAction(Long userId, Long videoId, ActionType type, boolean active) {
        if (videoId == null || videoId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO_ID);
        }
        if (!repository.isPublishedVideo(videoId)) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        boolean changed = active ? repository.activate(userId, videoId, type) : repository.cancel(userId, videoId, type);
        int delta = changed ? (active ? 1 : -1) : 0;
        if (delta != 0) {
            int hotDelta = delta * (type == ActionType.LIKE ? HotRanking.LIKE_WEIGHT : HotRanking.FAVORITE_WEIGHT);
            recordDeltaAfterCommit(videoId, type.statField(), delta, hotDelta);
            if (type == ActionType.LIKE && delta > 0) {
                notifyLike(userId, videoId);
            }
            // 点赞和收藏已经同步落库，热度与通知也在提交后完成，不再投递 interaction.action_changed。
        }
        // 前端以响应计数为准不做乐观更新，所以要叠加尚未刷库的增量和本次尚未记入缓冲的变化。
        VideoStat stat = repository.findStat(videoId).orElse(null);
        Map<VideoStatField, Long> pending = pendingDeltas(videoId);
        return new ActionVO(videoId, type.name(), active,
                currentCount(stat == null ? null : stat.getLikeCount(), pending, VideoStatField.LIKE_COUNT,
                        type == ActionType.LIKE ? delta : 0),
                currentCount(stat == null ? null : stat.getFavoriteCount(), pending, VideoStatField.FAVORITE_COUNT,
                        type == ActionType.FAVORITE ? delta : 0));
    }

    /** 只在点赞首次变为有效时通知作者。再次点赞命中同一事件，不会多一条消息。 */
    private void notifyLike(Long userId, Long videoId) {
        Long authorId = repository.publishedAuthorId(videoId).orElse(null);
        if (authorId == null || authorId.equals(userId)) {
            return;
        }
        ActorSnapshot actor = users.findActor(userId).orElse(null);
        messages.notifyAfterCommit(MessageDraft.notification(
                authorId,
                MessageType.LIKE.name(),
                "收到点赞",
                "点赞了你的视频",
                "interaction:like:" + videoId + ":" + userId,
                userId,
                actor == null ? null : actor.nickname(),
                actor == null ? null : actor.avatarUrl()));
    }

    /** 回滚的事务不能留下增量或热度；提交后的记录失败不改变已经成功的互动。 */
    private void recordDeltaAfterCommit(Long videoId, VideoStatField field, int delta, int hotDelta) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    statBuffer.add(videoId, field, delta);
                } catch (RuntimeException ex) {
                    log.warn("记录计数增量失败，等待对账纠正 videoId={} field={} delta={}", videoId, field, delta, ex);
                }
                hotRanking.add(videoId, hotDelta);
            }
        });
    }

    private Map<VideoStatField, Long> pendingDeltas(Long videoId) {
        try {
            return statBuffer.pending(videoId);
        } catch (RuntimeException ex) {
            log.warn("读取待刷库计数失败，响应只使用已刷库计数 videoId={}", videoId, ex);
            return Map.of();
        }
    }

    private int currentCount(Integer stored, Map<VideoStatField, Long> pending, VideoStatField field, int ownDelta) {
        long value = (stored == null ? 0 : stored) + pending.getOrDefault(field, 0L) + ownDelta;
        return (int) Math.max(0, value);
    }
}
