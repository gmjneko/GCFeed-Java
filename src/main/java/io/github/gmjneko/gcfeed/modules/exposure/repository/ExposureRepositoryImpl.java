package io.github.gmjneko.gcfeed.modules.exposure.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.gmjneko.gcfeed.modules.exposure.entity.Exposure;
import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;
import io.github.gmjneko.gcfeed.modules.exposure.mapper.ExposureMapper;
import io.github.gmjneko.gcfeed.modules.exposure.mapper.ViewEventMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 单表 CRUD 使用 MyBatis-Plus；仅原子曝光 upsert 使用方言 SQL。
 */
@Repository
public class ExposureRepositoryImpl implements ExposureRepository {

    private final ViewEventMapper viewEventMapper;
    private final ExposureMapper exposureMapper;

    public ExposureRepositoryImpl(ViewEventMapper viewEventMapper, ExposureMapper exposureMapper) {
        this.viewEventMapper = viewEventMapper;
        this.exposureMapper = exposureMapper;
    }

    @Override
    public int insertViewEvent(ViewEvent event) {
        return viewEventMapper.insert(event);
    }

    @Override
    public int upsertExposure(Exposure exposure) {
        return exposureMapper.upsertExposure(exposure);
    }

    @Override
    public Optional<Exposure> findByUserAndVideo(Long userId, Long videoId) {
        return Optional.ofNullable(exposureMapper.selectOne(Wrappers.<Exposure>lambdaQuery()
                .eq(Exposure::getUserId, userId)
                .eq(Exposure::getVideoId, videoId)));
    }

    @Override
    public List<ViewEvent> findInterestEvents(Long userId, LocalDateTime since, int limit) {
        // 样本上限按最新事件截取；秒级时间相同时以 ID 排序，关闭 count 避免无用全量计数。
        return viewEventMapper.selectPage(new Page<>(1, limit, false), Wrappers.<ViewEvent>lambdaQuery()
                .select(ViewEvent::getVideoId, ViewEvent::getEventType, ViewEvent::getWatchMs, ViewEvent::getCompleted)
                .eq(ViewEvent::getUserId, userId).ge(ViewEvent::getCreatedAt, since)
                .in(ViewEvent::getEventType, "play", "complete")
                .orderByDesc(ViewEvent::getCreatedAt, ViewEvent::getId)).getRecords();
    }

    @Override
    public Map<Long, Exposure> findRecent(Long userId, List<Long> videoIds, LocalDateTime since) {
        if (videoIds.isEmpty()) return Map.of();
        // 时间边界包含起点，且不加 scene 条件，与召回阶段的曝光过滤保持一致。
        return exposureMapper.selectList(Wrappers.<Exposure>lambdaQuery()
                        .eq(Exposure::getUserId, userId).in(Exposure::getVideoId, videoIds)
                        .ge(Exposure::getLastExposedAt, since)).stream()
                .collect(Collectors.toMap(Exposure::getVideoId, Function.identity()));
    }

}
