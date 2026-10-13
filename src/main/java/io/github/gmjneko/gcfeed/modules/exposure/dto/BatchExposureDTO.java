package io.github.gmjneko.gcfeed.modules.exposure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 内部曝光批次；身份由已通过内部令牌认证的调用方提供。
 */
public record BatchExposureDTO(
        @JsonProperty("user_id")
        Long userId,
        String scene,
        @JsonProperty("request_id")
        String requestId,
        @JsonProperty("video_ids")
        List<Long> videoIds
) {

}
