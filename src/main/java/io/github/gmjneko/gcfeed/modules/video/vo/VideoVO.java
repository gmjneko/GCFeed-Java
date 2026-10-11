package io.github.gmjneko.gcfeed.modules.video.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;

import java.time.LocalDateTime;

/** 视频详情和列表卡片响应。 */
public record VideoVO(
        Long id,
        @JsonProperty("author_id") Long authorId,
        String title,
        String description,
        @JsonProperty("media_url") String mediaUrl,
        @JsonProperty("cover_url") String coverUrl,
        Integer status,
        @JsonProperty("like_count") Integer likeCount,
        @JsonProperty("comment_count") Integer commentCount,
        @JsonProperty("favorite_count") Integer favoriteCount,
        @JsonProperty("published_at") LocalDateTime publishedAt,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {

    public static VideoVO from(Video video) {
        return new VideoVO(
                video.getId(), video.getAuthorId(), video.getTitle(), video.getDescription(),
                video.getMediaUrl(), video.getCoverUrl(), video.getStatus(),
                valueOrZero(video.getLikeCount()), valueOrZero(video.getCommentCount()),
                valueOrZero(video.getFavoriteCount()), video.getPublishedAt(),
                video.getCreatedAt(), video.getUpdatedAt()
        );
    }

    private static Integer valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
