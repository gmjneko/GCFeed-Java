package io.github.gmjneko.gcfeed.modules.video.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.annotation.Idempotent;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.embedding.service.VideoPublishedEvent;
import io.github.gmjneko.gcfeed.modules.video.dto.CreateVideoDTO;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import io.github.gmjneko.gcfeed.modules.video.repository.VideoRepository;
import io.github.gmjneko.gcfeed.modules.video.service.VideoService;
import io.github.gmjneko.gcfeed.modules.video.service.VideoCursorCodec;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoCursor;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoListVO;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 视频业务规则和事务边界。
 */
@Service
public class VideoServiceImpl implements VideoService {

    private final VideoRepository videoRepository;
    private final ApplicationEventPublisher eventPublisher;

    public VideoServiceImpl(VideoRepository videoRepository, ApplicationEventPublisher eventPublisher) {
        this.videoRepository = videoRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Idempotent(scope = "video.publish", userId = "#authorId", key = "#idempotencyKey")
    @Transactional(rollbackFor = Exception.class)
    public CreateResult createPublished(Long authorId, CreateVideoDTO dto, String idempotencyKey) {
        requirePositive(authorId, ErrorCode.INVALID_AUTHOR_ID);
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }

        String key = normalize(idempotencyKey);
        String title = required(
                dto.title(),
                CommonConstant.MAX_VIDEO_TITLE_LENGTH,
                ErrorCode.EMPTY_VIDEO_TITLE,
                ErrorCode.VIDEO_TITLE_TOO_LONG
        );
        String description = optional(
                dto.description(),
                CommonConstant.MAX_VIDEO_DESCRIPTION_LENGTH,
                ErrorCode.VIDEO_DESCRIPTION_TOO_LONG
        );
        String mediaUrl = required(
                dto.mediaUrl(),
                CommonConstant.MAX_MEDIA_URL_LENGTH,
                ErrorCode.EMPTY_MEDIA_URL,
                ErrorCode.MEDIA_URL_TOO_LONG
        );
        String coverUrl = required(
                dto.coverUrl(),
                CommonConstant.MAX_MEDIA_URL_LENGTH,
                ErrorCode.EMPTY_COVER_URL,
                ErrorCode.COVER_URL_TOO_LONG
        );

        // 发布键由 video 表唯一索引持久化；重复请求从业务记录重建当前响应。
        if (!key.isEmpty()) {
            Video existing = videoRepository.findByAuthorAndIdempotencyKey(authorId, key).orElse(null);
            if (existing != null) {
                if (!title.equals(existing.getTitle()) || !description.equals(normalize(existing.getDescription()))
                        || !mediaUrl.equals(existing.getMediaUrl()) || !coverUrl.equals(existing.getCoverUrl())) {
                    throw new BusinessException(ErrorCode.PUBLISH_REQUEST_CONFLICT);
                }
                return new CreateResult(VideoVO.from(existing), false);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Video video = Video.builder()
                .authorId(authorId)
                .title(title)
                .description(description)
                .mediaUrl(mediaUrl)
                .coverUrl(coverUrl)
                .status(CommonConstant.VIDEO_STATUS_PUBLISHED)
                .publishedAt(now)
                .idempotencyKey(key.isEmpty() ? null : key)
                .likeCount(0)
                .commentCount(0)
                .favoriteCount(0)
                .build();

        // 唯一索引作为持久化兜底；Redis 只处理短暂并发，不替代业务约束。
        videoRepository.insertVideo(video);

        videoRepository.insertStat(VideoStat.builder()
                .videoId(video.getId())
                .likeCount(0)
                .commentCount(0)
                .favoriteCount(0)
                .build());

        // 发布事件在事务提交后由监听器处理（见 VideoPublishedEvent 上的事务阶段约定）：
        // 事务内发布会让回滚留下指向不存在视频的派生数据，也会把下游耗时算进事务。
        eventPublisher.publishEvent(new VideoPublishedEvent(
                UUID.randomUUID().toString(),
                video.getId(),
                video.getAuthorId(),
                video.getTitle(),
                video.getDescription(),
                Instant.now()));

        // TODO(RabbitMQ): 用事务 Outbox 可靠发布 video.published；普通作者分发粉丝收件箱，大 V 维护发件箱。
        // TODO(RabbitMQ): 消费者实现幂等、重试和死信；禁止数据库事务提交前直接发送消息。
        return new CreateResult(VideoVO.from(video), true);
    }

    @Override
    public VideoVO get(Long videoId) {
        requirePositive(videoId, ErrorCode.INVALID_VIDEO_ID);
        Video video = videoRepository.findPublishedById(videoId).orElse(null);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return VideoVO.from(video);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Long lockForInteraction(Long videoId, boolean requirePublished) {
        requirePositive(videoId, ErrorCode.INVALID_VIDEO_ID);
        Video video = videoRepository.lockById(videoId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VIDEO_NOT_FOUND));
        if (requirePublished && video.getStatus() != CommonConstant.VIDEO_STATUS_PUBLISHED) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return video.getAuthorId();
    }

    @Override
    public VideoListVO listPublicByAuthor(Long authorId, int limit, String cursor) {
        validatePage(authorId, limit);
        int actualLimit = Math.min(limit, CommonConstant.MAX_PAGE_SIZE);
        return buildList(
                videoRepository.findPublicByAuthor(authorId, actualLimit + 1, VideoCursorCodec.decode(cursor)),
                actualLimit
        );
    }

    @Override
    public VideoListVO listMine(Long authorId, int limit, String cursor) {
        validatePage(authorId, limit);
        int actualLimit = Math.min(limit, CommonConstant.MAX_PAGE_SIZE);
        return buildList(videoRepository.findMineByAuthor(authorId, actualLimit + 1,
                VideoCursorCodec.decode(cursor)), actualLimit);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long authorId, Long videoId) {
        requirePositive(authorId, ErrorCode.INVALID_AUTHOR_ID);
        requirePositive(videoId, ErrorCode.INVALID_VIDEO_ID);

        Video video = videoRepository.findAnyStatusById(videoId).orElse(null);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        if (!authorId.equals(video.getAuthorId())) {
            throw new BusinessException(ErrorCode.VIDEO_PERMISSION_DENIED);
        }
        // DELETE 的业务幂等由状态判断完成，不需要 Idempotency-Key。
        if (isDeleted(video)) {
            return;
        }

        int affected = videoRepository.markDeleted(videoId);
        if (affected == 0) {
            // 处理并发删除：另一个请求已经把状态改为删除时仍然返回成功。
            Video latest = videoRepository.findAnyStatusById(videoId).orElse(null);
            if (latest != null && isDeleted(latest)) {
                return;
            }
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
    }

    private VideoListVO buildList(List<Video> videos, int limit) {
        boolean hasMore = videos.size() > limit;
        if (hasMore) {
            videos = videos.subList(0, limit);
        }
        List<VideoVO> items = videos.stream().map(VideoVO::from).toList();
        String nextCursor = videos.isEmpty() ? "" : VideoCursorCodec.encode(new VideoCursor(
                videos.get(videos.size() - 1).getPublishedAt(), videos.get(videos.size() - 1).getId()));
        return new VideoListVO(items, nextCursor, hasMore);
    }

    private void validatePage(Long authorId, int limit) {
        requirePositive(authorId, ErrorCode.INVALID_AUTHOR_ID);
        if (limit <= 0) {
            throw new BusinessException(ErrorCode.INVALID_LIMIT);
        }
    }

    private boolean isDeleted(Video video) {
        return Integer.valueOf(CommonConstant.VIDEO_STATUS_DELETED).equals(video.getStatus());
    }

    private void requirePositive(Long value, ErrorCode errorCode) {
        if (value == null || value <= 0) {
            throw new BusinessException(errorCode);
        }
    }

    private String required(String value, int maxLength, ErrorCode emptyCode, ErrorCode lengthCode) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            throw new BusinessException(emptyCode);
        }
        if (length(normalized) > maxLength) {
            throw new BusinessException(lengthCode);
        }
        return normalized;
    }

    private String optional(String value, int maxLength, ErrorCode lengthCode) {
        String normalized = normalize(value);
        if (length(normalized) > maxLength) {
            throw new BusinessException(lengthCode);
        }
        return normalized;
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    private int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
