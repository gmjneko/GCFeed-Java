package io.github.gmjneko.gcfeed.modules.interaction.service.impl;

import io.github.gmjneko.gcfeed.common.annotation.Idempotent;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.interaction.entity.InteractionAction;
import io.github.gmjneko.gcfeed.modules.interaction.model.ActionType;
import io.github.gmjneko.gcfeed.modules.interaction.repository.InteractionRepository;
import io.github.gmjneko.gcfeed.modules.interaction.service.InteractionService;
import io.github.gmjneko.gcfeed.modules.interaction.vo.ActionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 公开方法开启事务，私有方法统一处理状态与计数。 */
@Service
@Transactional(rollbackFor = Exception.class)
public class InteractionServiceImpl implements InteractionService {
    private final InteractionRepository repository;

    public InteractionServiceImpl(InteractionRepository repository) {
        this.repository = repository;
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
        // 锁顺序固定为视频 → 行为 → 统计；同视频的不同请求键也串行处理。
        repository.lockPublishedVideo(videoId).orElseThrow(() -> new BusinessException(ErrorCode.VIDEO_NOT_FOUND));
        InteractionAction action = repository.lockAction(userId, videoId, type).orElse(null);
        boolean wasActive = action != null && Integer.valueOf(0).equals(action.getDeleted())
                && Integer.valueOf(InteractionAction.STATUS_ACTIVE).equals(action.getStatus());
        int target = active ? InteractionAction.STATUS_ACTIVE : InteractionAction.STATUS_CANCELED;
        int delta = (active ? 1 : 0) - (wasActive ? 1 : 0);
        if (action == null) {
            // 首次 DELETE 也创建取消态记录，为后续状态转换保留稳定的唯一键。
            action = new InteractionAction();
            action.setUserId(userId);
            action.setVideoId(videoId);
            action.setActionType(type.name());
            action.setStatus(target);
            action.setDeleted(0);
            repository.insertAction(action);
        } else if (!Integer.valueOf(target).equals(action.getStatus()) || !Integer.valueOf(0).equals(action.getDeleted())) {
            // 墓碑视为未生效，恢复原行而不是再次插入，计数仍只取决于有效状态的变化。
            repository.setState(action.getId(), target);
        }
        var stat = repository.lockStat(videoId)
                .orElseThrow(() -> new IllegalStateException("published video has no active stat"));
        if (delta != 0) {
            repository.changeCount(videoId, type, delta);
            stat = repository.lockStat(videoId).orElseThrow();
            // TODO(RabbitMQ): 通过 Outbox/可靠的提交后机制发布 interaction.action_changed，禁止提交前发送。
            // TODO(RabbitMQ): 消费者需实现事件幂等、有限重试和死信，再接入热榜与通知（自赞不通知）。
        }
        // 无状态变化时返回当前状态与计数；请求键只拦截并发提交，不重放历史响应。
        return new ActionVO(videoId, type.name(), active, stat.getLikeCount(), stat.getFavoriteCount());
    }
}
