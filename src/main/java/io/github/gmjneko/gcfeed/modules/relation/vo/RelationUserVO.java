package io.github.gmjneko.gcfeed.modules.relation.vo;

import com.fasterxml.jackson.annotation.*;

import java.time.LocalDateTime;

public record RelationUserVO(
        @JsonProperty("user_id")
        Long userId,
        String nickname,
        @JsonProperty("avatar_url")
        String avatarUrl,
        String bio,
        @JsonProperty("followed_at")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime followedAt,
        boolean following
) {
}
