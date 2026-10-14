package io.github.gmjneko.gcfeed.modules.message.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.gmjneko.gcfeed.modules.message.entity.UserMessage;

import java.time.LocalDateTime;

public record MessageVO(
        Long id,
        @JsonProperty("user_id") Long userId,
        String type,
        String title,
        String content,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("actor_id") long actorId,
        @JsonProperty("actor_nickname") String actorNickname,
        @JsonProperty("actor_avatar_url") String actorAvatarUrl,
        @JsonProperty("is_read") boolean read,
        @JsonProperty("created_at") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        @JsonProperty("read_at") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime readAt
) {

    public static MessageVO from(UserMessage message) {
        return new MessageVO(
                message.getId(),
                message.getUserId(),
                message.getType(),
                message.getTitle(),
                message.getContent(),
                message.getEventId(),
                message.getActorId() == null ? 0L : message.getActorId(),
                message.getActorNickname(),
                message.getActorAvatarUrl(),
                message.getIsRead() != null && message.getIsRead() == 1,
                message.getCreatedAt(),
                message.getReadAt());
    }
}
