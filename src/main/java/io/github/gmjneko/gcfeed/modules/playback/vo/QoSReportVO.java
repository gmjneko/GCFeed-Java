package io.github.gmjneko.gcfeed.modules.playback.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.gmjneko.gcfeed.modules.playback.entity.PlaybackQosLog;

import java.time.LocalDateTime;

public record QoSReportVO(
        Long id,
        @JsonProperty("user_id") long userId,
        @JsonProperty("video_id") Long videoId,
        @JsonProperty("first_frame_ms") Integer firstFrameMs,
        @JsonProperty("stutter_count") int stutterCount,
        @JsonProperty("watch_ms") int watchMs,
        @JsonProperty("created_at") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt
) {

    public static QoSReportVO from(PlaybackQosLog log) {
        return new QoSReportVO(
                log.getId(),
                log.getUserId() == null ? 0L : log.getUserId(),
                log.getVideoId(),
                log.getFirstFrameMs(),
                log.getStutterCount() == null ? 0 : log.getStutterCount(),
                log.getWatchMs() == null ? 0 : log.getWatchMs(),
                log.getCreatedAt());
    }
}
