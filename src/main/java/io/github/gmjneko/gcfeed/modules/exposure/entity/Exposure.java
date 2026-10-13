package io.github.gmjneko.gcfeed.modules.exposure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 映射用户与视频维度的曝光聚合；首次时间保持不变，次数由数据库原子累加。 */
@TableName("exposures")
public class Exposure {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    private LocalDateTime firstExposedAt;
    private LocalDateTime lastExposedAt;
    private Integer exposureCount;
    private String lastScene;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 供 MyBatis 映射字段和回填自增主键。 */
    public Exposure() {
    }

    private Exposure(Builder builder) {
        this.id = builder.id;
        this.userId = builder.userId;
        this.videoId = builder.videoId;
        this.firstExposedAt = builder.firstExposedAt;
        this.lastExposedAt = builder.lastExposedAt;
        this.exposureCount = builder.exposureCount;
        this.lastScene = builder.lastScene;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
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

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public LocalDateTime getFirstExposedAt() {
        return firstExposedAt;
    }

    public void setFirstExposedAt(LocalDateTime firstExposedAt) {
        this.firstExposedAt = firstExposedAt;
    }

    public LocalDateTime getLastExposedAt() {
        return lastExposedAt;
    }

    public void setLastExposedAt(LocalDateTime lastExposedAt) {
        this.lastExposedAt = lastExposedAt;
    }

    public Integer getExposureCount() {
        return exposureCount;
    }

    public void setExposureCount(Integer exposureCount) {
        this.exposureCount = exposureCount;
    }

    public String getLastScene() {
        return lastScene;
    }

    public void setLastScene(String lastScene) {
        this.lastScene = lastScene;
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

    /** 每次 build 返回独立实体，避免复用构建器修改已构建的对象。 */
    public static final class Builder {

        private Long id;
        private Long userId;
        private Long videoId;
        private LocalDateTime firstExposedAt;
        private LocalDateTime lastExposedAt;
        private Integer exposureCount;
        private String lastScene;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public Builder id(Long value) {
            this.id = value;
            return this;
        }

        public Builder userId(Long value) {
            this.userId = value;
            return this;
        }

        public Builder videoId(Long value) {
            this.videoId = value;
            return this;
        }

        public Builder firstExposedAt(LocalDateTime value) {
            this.firstExposedAt = value;
            return this;
        }

        public Builder lastExposedAt(LocalDateTime value) {
            this.lastExposedAt = value;
            return this;
        }

        public Builder exposureCount(Integer value) {
            this.exposureCount = value;
            return this;
        }

        public Builder lastScene(String value) {
            this.lastScene = value;
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

        public Exposure build() {
            return new Exposure(this);
        }

    }

}
