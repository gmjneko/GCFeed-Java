package io.github.gmjneko.gcfeed.modules.message.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateMessageDTO(
        @JsonProperty("user_id") Long userId,
        String type,
        String title,
        String content,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("actor_id") Long actorId,
        @JsonProperty("actor_nickname") String actorNickname,
        @JsonProperty("actor_avatar_url") String actorAvatarUrl
) {
}
