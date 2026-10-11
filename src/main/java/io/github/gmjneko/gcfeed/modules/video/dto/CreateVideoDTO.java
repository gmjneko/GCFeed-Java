package io.github.gmjneko.gcfeed.modules.video.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 接收视频发布信息；字段清洗和业务长度校验由 Service 统一执行。 */
public record CreateVideoDTO(
        String title,
        String description,
        @JsonProperty("media_url") String mediaUrl,
        @JsonProperty("cover_url") String coverUrl
) {
}
