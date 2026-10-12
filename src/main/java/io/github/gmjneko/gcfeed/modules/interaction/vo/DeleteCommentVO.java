package io.github.gmjneko.gcfeed.modules.interaction.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeleteCommentVO(
        @JsonProperty("comment_id")
        Long commentId,
        int status,
        @JsonProperty("comment_count")
        int commentCount
) {
}
