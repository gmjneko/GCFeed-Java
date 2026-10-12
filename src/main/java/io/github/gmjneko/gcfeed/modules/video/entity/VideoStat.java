package io.github.gmjneko.gcfeed.modules.video.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 映射 video_stat 表，保存高频互动计数。 */
@TableName("video_stat")
public class VideoStat {

    @TableId
    private Long videoId;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public VideoStat() {
    }

    private VideoStat(Builder builder) {
        this.videoId = builder.videoId;
        this.likeCount = builder.likeCount;
        this.commentCount = builder.commentCount;
        this.favoriteCount = builder.favoriteCount;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public Integer getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(Integer likeCount) {
        this.likeCount = likeCount;
    }

    public Integer getCommentCount() {
        return commentCount;
    }

    public void setCommentCount(Integer commentCount) {
        this.commentCount = commentCount;
    }

    public Integer getFavoriteCount() {
        return favoriteCount;
    }

    public void setFavoriteCount(Integer favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static final class Builder {
        private Long videoId;
        private Integer likeCount;
        private Integer commentCount;
        private Integer favoriteCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder videoId(Long value) {
            this.videoId = value;
            return this;
        }

        public Builder likeCount(Integer value) {
            this.likeCount = value;
            return this;
        }

        public Builder commentCount(Integer value) {
            this.commentCount = value;
            return this;
        }

        public Builder favoriteCount(Integer value) {
            this.favoriteCount = value;
            return this;
        }

        public Builder createdAt(LocalDateTime value) {
            this.createdAt = value;
            return this;
        }

        public Builder updatedAt(LocalDateTime value) {
            this.updatedAt = value;
            return this;
        }

        public VideoStat build() {
            return new VideoStat(this);
        }
    }
}
