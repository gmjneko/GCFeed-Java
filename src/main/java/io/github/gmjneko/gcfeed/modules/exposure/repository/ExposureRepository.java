package io.github.gmjneko.gcfeed.modules.exposure.repository;

import io.github.gmjneko.gcfeed.modules.exposure.entity.Exposure;
import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;

import java.util.Optional;

/** 封装观看流水与曝光聚合的持久化；事务边界由 Service 统一管理。 */
public interface ExposureRepository {

    /** 每次调用插入一条新流水并回填主键，不按 request_id 去重。 */
    int insertViewEvent(ViewEvent event);

    /** 原子记录一次曝光，返回影响行数而非曝光次数。 */
    int upsertExposure(Exposure exposure);

    /** 回查聚合的实际次数、首次时间与最近时间。 */
    Optional<Exposure> findByUserAndVideo(Long userId, Long videoId);

}
