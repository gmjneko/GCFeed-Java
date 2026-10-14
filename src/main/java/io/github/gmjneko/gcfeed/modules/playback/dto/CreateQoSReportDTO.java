package io.github.gmjneko.gcfeed.modules.playback.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 播放质量上报。用户接口忽略 {@code userId}，内部接口才从请求体读取。 */
public record CreateQoSReportDTO(
        @JsonProperty("user_id") Long userId,
        @JsonProperty("video_id") Long videoId,
        @JsonProperty("first_frame_ms") Integer firstFrameMs,
        @JsonProperty("stutter_count") Integer stutterCount,
        @JsonProperty("watch_ms") Integer watchMs
) {
}
