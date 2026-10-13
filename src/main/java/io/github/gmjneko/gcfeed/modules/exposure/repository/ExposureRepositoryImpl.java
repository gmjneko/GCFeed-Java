package io.github.gmjneko.gcfeed.modules.exposure.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.gmjneko.gcfeed.modules.exposure.entity.Exposure;
import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;
import io.github.gmjneko.gcfeed.modules.exposure.mapper.ExposureMapper;
import io.github.gmjneko.gcfeed.modules.exposure.mapper.ViewEventMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** 单表 CRUD 使用 MyBatis-Plus；仅原子曝光 upsert 使用方言 SQL。 */
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

}
