package io.github.gmjneko.gcfeed.modules.recommendation.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 最近曝光判定，未曝光时省略最近曝光时间。
 */
public record ExposureDecisionVO(
        @JsonProperty("video_id") Long videoId,
        boolean allowed,
        String reason,
        @JsonProperty("last_exposed_at") @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime lastExposedAt
) {

}
