package io.github.gmjneko.gcfeed.modules.exposure.repository;

import io.github.gmjneko.gcfeed.modules.exposure.entity.Exposure;
import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;

/**
 * 封装观看流水与曝光聚合的持久化；事务边界由 Service 统一管理。
 */
public interface ExposureRepository {

    /**
     * 每次调用插入一条新流水并回填主键，不按 request_id 去重。
     */
    int insertViewEvent(ViewEvent event);

    /**
     * 原子记录一次曝光，返回影响行数而非曝光次数。
     */
    int upsertExposure(Exposure exposure);

    /**
     * 回查聚合的实际次数、首次时间与最近时间。
     */
    Optional<Exposure> findByUserAndVideo(Long userId, Long videoId);

    /**
     * 读取 since 起最近的 play、complete 流水，按事件时间和 ID 降序截取画像所需样本。
     */
    List<ViewEvent> findInterestEvents(Long userId, LocalDateTime since, int limit);

    /**
     * 按视频 ID 批量读取最近曝光，包含 since 边界且跨 scene 共用；空列表不查库。
     */
    Map<Long, Exposure> findRecent(Long userId, List<Long> videoIds, LocalDateTime since);

}
