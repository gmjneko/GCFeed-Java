package io.github.gmjneko.gcfeed.modules.exposure.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 映射观看流水表，每次上报保存一行；业务默认值由 Service 显式指定。 */
@TableName("video_view_events")
public class ViewEvent {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    private String scene;
    private String requestId;
    private String eventType;
    private Integer watchMs;
    private Boolean completed;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 供 MyBatis 映射字段和回填自增主键。 */
    public ViewEvent() {
    }

    private ViewEvent(Builder builder) {
        this.id = builder.id;
        this.userId = builder.userId;
        this.videoId = builder.videoId;
        this.scene = builder.scene;
        this.requestId = builder.requestId;
        this.eventType = builder.eventType;
        this.watchMs = builder.watchMs;
        this.completed = builder.completed;
        this.createdAt = builder.createdAt;
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

    public String getScene() {
        return scene;
    }

    public void setScene(String scene) {
        this.scene = scene;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getWatchMs() {
        return watchMs;
    }

    public void setWatchMs(Integer watchMs) {
        this.watchMs = watchMs;
    }

    public Boolean getCompleted() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 每次 build 返回独立实体，避免复用构建器修改已构建的对象。 */
    public static final class Builder {

        private Long id;
        private Long userId;
        private Long videoId;
        private String scene;
        private String requestId;
        private String eventType;
        private Integer watchMs;
        private Boolean completed;
        private LocalDateTime createdAt;

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

        public Builder scene(String value) {
            this.scene = value;
            return this;
        }

        public Builder requestId(String value) {
            this.requestId = value;
            return this;
        }

        public Builder eventType(String value) {
            this.eventType = value;
            return this;
        }

        public Builder watchMs(Integer value) {
            this.watchMs = value;
            return this;
        }

        public Builder completed(Boolean value) {
            this.completed = value;
            return this;
        }

        public Builder createdAt(LocalDateTime value) {
            this.createdAt = value;
            return this;
        }

        public ViewEvent build() {
            return new ViewEvent(this);
        }

    }

}
