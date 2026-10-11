package io.github.gmjneko.gcfeed.modules.feed.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/** Feed 展示卡片，不包含账号、密码等用户私有字段。 */
public record FeedItemVO(
        @JsonProperty("video_id") Long videoId,
        @JsonProperty("author_id") Long authorId,
        @JsonProperty("author_nickname") String authorNickname,
        @JsonProperty("author_avatar_url") String authorAvatarUrl,
        String title,
        String description,
        @JsonProperty("media_url") String mediaUrl,
        @JsonProperty("cover_url") String coverUrl,
        @JsonProperty("like_count") int likeCount,
        @JsonProperty("comment_count") int commentCount,
        @JsonProperty("favorite_count") int favoriteCount,
        boolean liked,
        boolean favorited,
        @JsonProperty("published_at") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime publishedAt
) {
}
