package io.github.gmjneko.gcfeed.modules.exposure.vo;

import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** 新建观看流水的响应；未提供 request_id 时省略该字段。 */
public record ViewEventVO(
        Long id,
        @JsonProperty("user_id")
        Long userId,
        @JsonProperty("video_id")
        Long videoId,
        String scene,
        @JsonProperty("request_id")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String requestId,
        @JsonProperty("event_type")
        String eventType,
        @JsonProperty("watch_ms")
        Integer watchMs,
        Boolean completed,
        @JsonProperty("created_at")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt
) {

    public static ViewEventVO from(ViewEvent event) {
        return new ViewEventVO(event.getId(), event.getUserId(), event.getVideoId(), event.getScene(),
                event.getRequestId(), event.getEventType(), event.getWatchMs(), event.getCompleted(), event.getCreatedAt());
    }

}
