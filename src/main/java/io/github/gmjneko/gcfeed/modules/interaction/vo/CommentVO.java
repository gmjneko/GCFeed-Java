package io.github.gmjneko.gcfeed.modules.interaction.vo;

import com.fasterxml.jackson.annotation.*;

import java.time.LocalDateTime;

public record CommentVO(
        Long id,
        @JsonProperty("video_id")
        Long videoId,
        @JsonProperty("user_id")
        Long userId,
        @JsonProperty("user_nickname")
        String userNickname,
        @JsonProperty("user_avatar_url")
        String userAvatarUrl, String content,
        @JsonProperty("created_at")
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt,
        @JsonProperty("comment_count")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer commentCount
) {
}
