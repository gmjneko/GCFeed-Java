package io.github.gmjneko.gcfeed.modules.interaction.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ActionVO(
        @JsonProperty("video_id") Long videoId,
        @JsonProperty("action_type") String actionType,
        boolean active,
        @JsonProperty("like_count") int likeCount,
        @JsonProperty("favorite_count") int favoriteCount
) {
}
