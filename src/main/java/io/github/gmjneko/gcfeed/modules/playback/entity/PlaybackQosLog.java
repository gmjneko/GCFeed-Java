package io.github.gmjneko.gcfeed.modules.playback.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 一次播放质量上报。空幂等键保持 NULL，唯一索引才不会把未要求幂等的上报合成一条。 */
@TableName("playback_qos_log")
public class PlaybackQosLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    private Integer firstFrameMs;
    private Integer stutterCount;
    private Integer watchMs;
    private String idempotencyKey;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

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

    public Integer getFirstFrameMs() {
        return firstFrameMs;
    }

    public void setFirstFrameMs(Integer firstFrameMs) {
        this.firstFrameMs = firstFrameMs;
    }

    public Integer getStutterCount() {
        return stutterCount;
    }

    public void setStutterCount(Integer stutterCount) {
        this.stutterCount = stutterCount;
    }

    public Integer getWatchMs() {
        return watchMs;
    }

    public void setWatchMs(Integer watchMs) {
        this.watchMs = watchMs;
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
}
