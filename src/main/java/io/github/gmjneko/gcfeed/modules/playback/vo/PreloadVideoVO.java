package io.github.gmjneko.gcfeed.modules.playback.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;

public record PreloadVideoVO(
        @JsonProperty("video_id") Long videoId,
        @JsonProperty("media_url") String mediaUrl,
        @JsonProperty("cover_url") String coverUrl
) {

    public static PreloadVideoVO from(Video video) {
        return new PreloadVideoVO(video.getId(), video.getMediaUrl(), video.getCoverUrl());
    }
}
