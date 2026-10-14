package io.github.gmjneko.gcfeed.modules.playback.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.gmjneko.gcfeed.modules.playback.entity.PlaybackConfig;

import java.time.LocalDateTime;

public record PlaybackConfigVO(
        Long id,
        String platform,
        @JsonProperty("network_type") String networkType,
        @JsonProperty("preload_count") int preloadCount,
        @JsonProperty("buffer_ms") int bufferMs,
        @JsonProperty("updated_at") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt
) {

    public static PlaybackConfigVO from(PlaybackConfig config) {
        return new PlaybackConfigVO(
                config.getId(),
                config.getPlatform(),
                config.getNetworkType(),
                config.getPreloadCount(),
                config.getBufferMs(),
                config.getUpdatedAt());
    }
}
