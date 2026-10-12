package io.github.gmjneko.gcfeed.modules.interaction.service;

import io.github.gmjneko.gcfeed.common.annotation.Idempotent;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.pagination.*;
import io.github.gmjneko.gcfeed.modules.interaction.entity.InteractionComment;
import io.github.gmjneko.gcfeed.modules.interaction.repository.CommentRepository;
import io.github.gmjneko.gcfeed.modules.interaction.vo.*;
import io.github.gmjneko.gcfeed.modules.user.entity.User;
import io.github.gmjneko.gcfeed.modules.video.service.VideoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CommentService {
    private final CommentRepository repository;
    private final VideoService videos;

    public CommentService(CommentRepository repository, VideoService videos) {
        this.repository = repository;
        this.videos = videos;
    }

    public record CreateResult(CommentVO comment, boolean created) {
    }

    @Idempotent(scope = "comment.create", userId = "#userId", key = "#key")
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CreateResult create(Long userId, Long videoId, String content, String key) {
        positive(videoId, ErrorCode.INVALID_VIDEO_ID);
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) throw new BusinessException(ErrorCode.EMPTY_COMMENT_CONTENT);
        if (text.codePointCount(0, text.length()) > 1000)
            throw new BusinessException(ErrorCode.COMMENT_CONTENT_TOO_LONG);
        String requestKey = key == null ? "" : key.strip();
        repository.lockAuthor(userId);
        // 统一锁顺序：用户 -> 视频 -> 评论；删除路径也先锁视频，再锁评论。
        videos.lockForInteraction(videoId, true);
        if (!requestKey.isEmpty()) {
            InteractionComment existing = repository.byKey(userId, requestKey);
            if (existing != null) {
                if (!videoId.equals(existing.getVideoId()) || !text.equals(existing.getContent()) || existing.getStatus() != 1) {
                    throw new BusinessException(ErrorCode.COMMENT_REQUEST_CONFLICT);
                }
                return new CreateResult(view(existing, repository.count(videoId)), false);
            }
        }
        InteractionComment comment = new InteractionComment();
        comment.setUserId(userId);
        comment.setVideoId(videoId);
        comment.setContent(text);
        comment.setStatus(1);
        comment.setIdempotencyKey(requestKey.isEmpty() ? null : requestKey);
        repository.insert(comment);
        repository.changeCount(videoId, 1);
        // TODO(RabbitMQ): 事务 Outbox 记录 comment.created，提交后可靠发布；消费端幂等、重试、死信。
        // TODO(RabbitMQ): 评论热榜 +5，通知视频作者（自己评论自己不通知）；禁止提交前直接发送。
        return new CreateResult(view(comment, repository.count(videoId)), true);
    }

    public CursorPage<CommentVO> list(Long videoId, String cursor, int limit) {
        positive(videoId, ErrorCode.INVALID_VIDEO_ID);
        if (limit <= 0) throw new BusinessException(ErrorCode.INVALID_LIMIT);
        TimeIdCursor position = TimeIdCursorCodec.decode(cursor);
        videos.get(videoId);
        int size = Math.min(limit, 100);
        List<InteractionComment> rows = repository.page(videoId, position, size + 1);
        boolean more = rows.size() > size;
        List<InteractionComment> page = more ? rows.subList(0, size) : rows;
        var authors = repository.authors(page.stream().map(InteractionComment::getUserId).distinct().toList());
        var items = page.stream().map(c -> view(c, authors.get(c.getUserId()), null)).toList();
        String next = page.isEmpty() ? "" : TimeIdCursorCodec.encode(new TimeIdCursor(
                page.getLast().getCreatedAt(), page.getLast().getId()));
        return new CursorPage<>(items, next, more);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public DeleteCommentVO delete(Long userId, String role, Long commentId) {
        positive(commentId, ErrorCode.INVALID_COMMENT_ID);
        InteractionComment initial = repository.find(commentId, false);
        if (initial == null) throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        Long authorId = videos.lockForInteraction(initial.getVideoId(), false);
        InteractionComment comment = repository.find(commentId, true);
        if (comment == null) throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
        if (!userId.equals(comment.getUserId()) && !userId.equals(authorId) && !"admin".equals(role)) {
            throw new BusinessException(ErrorCode.COMMENT_PERMISSION_DENIED);
        }
        if (repository.delete(commentId)) {
            repository.changeCount(comment.getVideoId(), -1);
            // TODO(RabbitMQ): 事务 Outbox 记录 comment.deleted，消费端幂等更新热榜 -5、重试及死信。
            // TODO(RabbitMQ): 仅实际删除时产生事件，禁止提交前直接发布。
        }
        return new DeleteCommentVO(commentId, 2, repository.count(comment.getVideoId()));
    }

    private CommentVO view(InteractionComment c, Integer count) {
        return view(c, repository.authors(List.of(c.getUserId())).get(c.getUserId()), count);
    }

    private CommentVO view(InteractionComment c, User user, Integer count) {
        return new CommentVO(c.getId(), c.getVideoId(), c.getUserId(), user == null ? null : user.getNickname(),
                user == null ? null : user.getAvatarUrl(), c.getContent(), c.getCreatedAt(), count);
    }

    private void positive(Long value, ErrorCode error) {
        if (value == null || value <= 0) throw new BusinessException(error);
    }
}
