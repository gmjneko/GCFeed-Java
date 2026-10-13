package io.github.gmjneko.gcfeed.modules.exposure.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.exposure.dto.BatchExposureDTO;
import io.github.gmjneko.gcfeed.modules.exposure.dto.CreateViewEventDTO;
import io.github.gmjneko.gcfeed.modules.exposure.entity.Exposure;
import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;
import io.github.gmjneko.gcfeed.modules.exposure.model.ExposureInputNormalizer;
import io.github.gmjneko.gcfeed.modules.exposure.model.ViewEventType;
import io.github.gmjneko.gcfeed.modules.exposure.repository.ExposureRepository;
import io.github.gmjneko.gcfeed.modules.exposure.service.ExposureService;
import io.github.gmjneko.gcfeed.modules.exposure.vo.BatchExposureVO;
import io.github.gmjneko.gcfeed.modules.exposure.vo.ExposureVO;
import io.github.gmjneko.gcfeed.modules.exposure.vo.RecordViewEventVO;
import io.github.gmjneko.gcfeed.modules.exposure.vo.ViewEventVO;
import io.github.gmjneko.gcfeed.modules.video.repository.VideoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 统一清洗观看信号，公共单条与内部批次共用写入逻辑和事务内回查。 */
@Service
public class ExposureServiceImpl implements ExposureService {

    private final ExposureRepository exposureRepository;
    private final VideoRepository videoRepository;
    private final Clock clock;

    public ExposureServiceImpl(ExposureRepository exposureRepository, VideoRepository videoRepository, Clock clock) {
        this.exposureRepository = exposureRepository;
        this.videoRepository = videoRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RecordViewEventVO recordViewEvent(Long userId, CreateViewEventDTO dto) {
        if (userId == null || userId <= 0) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        if (dto == null) throw new BusinessException(ErrorCode.PARAM_ERROR);
        if (dto.videoId() == null || dto.videoId() <= 0) throw new BusinessException(ErrorCode.INVALID_VIDEO_ID);
        String scene = ExposureInputNormalizer.scene(dto.scene());
        String requestId = ExposureInputNormalizer.requestId(dto.requestId());
        int watchMs = dto.watchMs() == null ? 0 : dto.watchMs();
        if (watchMs < 0) throw new BusinessException(ErrorCode.INVALID_WATCH_MS);
        ViewEventType type = ViewEventType.normalize(dto.eventType());
        boolean completed = type == ViewEventType.COMPLETE || Boolean.TRUE.equals(dto.completed());
        try {
            if (!videoRepository.existsPublished(dto.videoId())) throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
            return persist(userId, dto.videoId(), scene, requestId, type, watchMs, completed, now());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SAVE_VIEW_EVENT_FAILED, ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BatchExposureVO saveExposures(BatchExposureDTO dto) {
        if (dto == null) throw new BusinessException(ErrorCode.PARAM_ERROR);
        ExposureInputNormalizer.userId(dto.userId());
        String scene = ExposureInputNormalizer.scene(dto.scene());
        String requestId = ExposureInputNormalizer.requestId(dto.requestId());
        List<Long> ids = ExposureInputNormalizer.videoIds(dto.videoIds());
        if (ids.isEmpty()) return new BatchExposureVO(List.of());
        try {
            if (videoRepository.findPublishedIds(ids).size() != ids.size()) {
                throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
            }
            // 同批流水与聚合共用秒级时间，响应按原输入恢复，与数据库的固定锁顺序分开。
            LocalDateTime occurredAt = now();
            Map<Long, ExposureVO> result = new HashMap<>();
            // 固定锁顺序，避免两个请求以相反视频顺序更新相同聚合而互相等待。
            for (Long id : ids.stream().sorted().toList()) {
                result.put(id, persist(dto.userId(), id, scene, requestId, ViewEventType.EXPOSED,
                        0, false, occurredAt).exposure());
            }
            return new BatchExposureVO(ids.stream().map(result::get).toList());
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.SAVE_VIEW_EVENT_FAILED, ex);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
    }

    // 调用方的 public 事务覆盖整个写入和回查，私有方法不另开事务，批次失败才能整体回滚。
    private RecordViewEventVO persist(Long userId, Long videoId, String scene, String requestId,
                                      ViewEventType type, int watchMs, boolean completed, LocalDateTime occurredAt) {
        ViewEvent event = ViewEvent.builder().userId(userId).videoId(videoId).scene(scene).requestId(requestId)
                .eventType(type.getValue()).watchMs(watchMs).completed(completed).createdAt(occurredAt).build();
        if (exposureRepository.insertViewEvent(event) <= 0 || event.getId() == null) {
            throw new BusinessException(ErrorCode.SAVE_VIEW_EVENT_FAILED);
        }
        ExposureVO aggregate = null;
        if (type == ViewEventType.EXPOSED) {
            Exposure exposure = Exposure.builder().userId(userId).videoId(videoId).firstExposedAt(occurredAt)
                    .lastExposedAt(occurredAt).exposureCount(1).lastScene(scene).build();
            // MySQL 重复更新的影响行数可能为 2，只检查成功，不把它当作累计曝光次数。
            if (exposureRepository.upsertExposure(exposure) <= 0) {
                throw new BusinessException(ErrorCode.SAVE_VIEW_EVENT_FAILED);
            }
            // 原子 SQL 已处理并发自增；必须在同一事务内回查，才能返回真实次数并发现缺失聚合。
            aggregate = ExposureVO.from(exposureRepository.findByUserAndVideo(userId, videoId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.SAVE_VIEW_EVENT_FAILED)));
        }
        // TODO(RabbitMQ): 当前推荐画像按需查表，明确有消费者后再设计提交后投递。
        return new RecordViewEventVO(ViewEventVO.from(event), aggregate);
    }

}
