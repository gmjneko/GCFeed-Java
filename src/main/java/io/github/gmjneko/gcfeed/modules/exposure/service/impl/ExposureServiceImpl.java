package io.github.gmjneko.gcfeed.modules.exposure.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.exposure.dto.CreateViewEventDTO;
import io.github.gmjneko.gcfeed.modules.exposure.entity.Exposure;
import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;
import io.github.gmjneko.gcfeed.modules.exposure.model.ViewEventType;
import io.github.gmjneko.gcfeed.modules.exposure.repository.ExposureRepository;
import io.github.gmjneko.gcfeed.modules.exposure.service.ExposureService;
import io.github.gmjneko.gcfeed.modules.exposure.vo.ExposureVO;
import io.github.gmjneko.gcfeed.modules.exposure.vo.RecordViewEventVO;
import io.github.gmjneko.gcfeed.modules.exposure.vo.ViewEventVO;
import io.github.gmjneko.gcfeed.modules.video.repository.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** 统一清洗观看信号，编排视频校验、流水写入、曝光原子累加和事务内回查。 */
@Service
public class ExposureServiceImpl implements ExposureService {

    private static final int MAX_SCENE_LENGTH = 32;
    private static final int MAX_REQUEST_ID_LENGTH = 64;

    private final ExposureRepository exposureRepository;
    private final VideoRepository videoRepository;

    public ExposureServiceImpl(ExposureRepository exposureRepository, VideoRepository videoRepository) {
        this.exposureRepository = exposureRepository;
        this.videoRepository = videoRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordViewEventVO recordViewEvent(Long userId, CreateViewEventDTO dto) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if (dto.videoId() == null || dto.videoId() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_VIDEO_ID);
        }

        // 曝光可来自尚未开放的 Feed 场景，不能用 FeedScene 拒绝 hot/recommend 等行为信号。
        String scene = strip(dto.scene()).toLowerCase(Locale.ROOT);
        if (scene.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_SCENE);
        }
        if (length(scene) > MAX_SCENE_LENGTH) {
            throw new BusinessException(ErrorCode.SCENE_TOO_LONG);
        }
        String requestId = strip(dto.requestId());
        if (length(requestId) > MAX_REQUEST_ID_LENGTH) {
            throw new BusinessException(ErrorCode.REQUEST_ID_TOO_LONG);
        }
        int watchMs = dto.watchMs() == null ? 0 : dto.watchMs();
        if (watchMs < 0) {
            throw new BusinessException(ErrorCode.INVALID_WATCH_MS);
        }
        ViewEventType eventType = ViewEventType.normalize(dto.eventType());
        boolean completed = eventType == ViewEventType.COMPLETE || Boolean.TRUE.equals(dto.completed());

        try {
            if (!videoRepository.existsPublished(dto.videoId())) {
                throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
            }

            // 与 DATETIME 的秒精度对齐，流水、聚合和响应共享同一个时间，不分别取 NOW()。
            LocalDateTime occurredAt = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
            ViewEvent event = ViewEvent.builder()
                    .userId(userId)
                    .videoId(dto.videoId())
                    .scene(scene)
                    .requestId(requestId.isEmpty() ? null : requestId)
                    .eventType(eventType.getValue())
                    .watchMs(watchMs)
                    .completed(completed)
                    .createdAt(occurredAt)
                    .build();
            if (exposureRepository.insertViewEvent(event) <= 0 || event.getId() == null) {
                throw new BusinessException(ErrorCode.SAVE_VIEW_EVENT_FAILED);
            }

            ExposureVO exposureVO = null;
            if (eventType == ViewEventType.EXPOSED) {
                Exposure exposure = Exposure.builder()
                        .userId(userId)
                        .videoId(dto.videoId())
                        .firstExposedAt(occurredAt)
                        .lastExposedAt(occurredAt)
                        .exposureCount(1)
                        .lastScene(scene)
                        .build();
                if (exposureRepository.upsertExposure(exposure) <= 0) {
                    throw new BusinessException(ErrorCode.SAVE_VIEW_EVENT_FAILED);
                }
                Exposure saved = exposureRepository.findByUserAndVideo(userId, dto.videoId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SAVE_VIEW_EVENT_FAILED));
                exposureVO = ExposureVO.from(saved);
            }

            // TODO(RabbitMQ): 当前推荐画像按需查表，不需要观看事件；明确有消费者后再设计提交后投递。
            return new RecordViewEventVO(ViewEventVO.from(event), exposureVO);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SAVE_VIEW_EVENT_FAILED, ex);
        }
    }

    private String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private int length(String value) {
        return value.codePointCount(0, value.length());
    }

}
