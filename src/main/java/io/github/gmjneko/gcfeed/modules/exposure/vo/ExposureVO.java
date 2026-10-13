package io.github.gmjneko.gcfeed.modules.exposure.vo;

import io.github.gmjneko.gcfeed.modules.exposure.entity.Exposure;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** 事务内回查的实际曝光聚合，不暴露内部主键和维护时间。 */
public record ExposureVO(
        @JsonProperty("user_id")
        Long userId,
        @JsonProperty("video_id")
        Long videoId,
        @JsonProperty("first_exposed_at")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime firstExposedAt,
        @JsonProperty("last_exposed_at")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime lastExposedAt,
        @JsonProperty("exposure_count")
        Integer exposureCount,
        @JsonProperty("last_scene")
        String lastScene
) {

    public static ExposureVO from(Exposure exposure) {
        return new ExposureVO(exposure.getUserId(), exposure.getVideoId(), exposure.getFirstExposedAt(),
                exposure.getLastExposedAt(), exposure.getExposureCount(), exposure.getLastScene());
    }

}
