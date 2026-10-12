package io.github.gmjneko.gcfeed.modules.video.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 映射 video 表，并承接视频详情查询时拼接出的统计快照。 */
@TableName("video")
public class Video {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long authorId;
    private String title;
    private String description;
    private String mediaUrl;
    private String coverUrl;
    /** 状态：1 草稿 2 上线 3 下架 4 删除。 */
    private Integer status;
    private LocalDateTime publishedAt;
    private String idempotencyKey;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 以下字段来自 video_stat 联查，不写入 video 表。 */
    @TableField(exist = false)
    private Integer likeCount;
    @TableField(exist = false)
    private Integer commentCount;
    @TableField(exist = false)
    private Integer favoriteCount;

    /** 供 MyBatis 创建实体并回填数据库字段。 */
    public Video() {
    }

    private Video(Builder builder) {
        this.id = builder.id;
        this.authorId = builder.authorId;
        this.title = builder.title;
        this.description = builder.description;
        this.mediaUrl = builder.mediaUrl;
        this.coverUrl = builder.coverUrl;
        this.status = builder.status;
        this.publishedAt = builder.publishedAt;
        this.idempotencyKey = builder.idempotencyKey;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.likeCount = builder.likeCount;
        this.commentCount = builder.commentCount;
        this.favoriteCount = builder.favoriteCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public void setCoverUrl(String coverUrl) {
        this.coverUrl = coverUrl;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    /** 业务创建和测试数据使用的手写 Builder。 */
    public static final class Builder {
        private Long id;
        private Long authorId;
        private String title;
        private String description;
        private String mediaUrl;
        private String coverUrl;
        private Integer status;
        private LocalDateTime publishedAt;
        private String idempotencyKey;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Integer likeCount;
        private Integer commentCount;
        private Integer favoriteCount;

        public Builder id(Long value) {
            this.id = value;
            return this;
        }

        public Builder authorId(Long value) {
            this.authorId = value;
            return this;
        }

        public Builder title(String value) {
            this.title = value;
            return this;
        }

        public Builder description(String value) {
            this.description = value;
            return this;
        }

        public Builder mediaUrl(String value) {
            this.mediaUrl = value;
            return this;
        }

        public Builder coverUrl(String value) {
            this.coverUrl = value;
            return this;
        }

        public Builder status(Integer value) {
            this.status = value;
            return this;
        }

        public Builder publishedAt(LocalDateTime value) {
            this.publishedAt = value;
            return this;
        }

        public Builder idempotencyKey(String value) {
            this.idempotencyKey = value;
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

        public Video build() {
            return new Video(this);
        }
    }
}
