package io.github.gmjneko.gcfeed.modules.exposure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 接收观看行为；身份由 token 提供，归一化、默认值和业务校验统一由 Service 执行。 */
public record CreateViewEventDTO(
        @JsonProperty("video_id")
        Long videoId,
        String scene,
        @JsonProperty("request_id")
        String requestId,
        @JsonProperty("event_type")
        String eventType,
        @JsonProperty("watch_ms")
        Integer watchMs,
        Boolean completed
) {

}
