package io.github.gmjneko.gcfeed.modules.playback.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 按端和网络类型保存的播放参数。读取时再做范围钳制，避免手工数据直接下发到端上。 */
@TableName("playback_config")
public class PlaybackConfig {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String platform;
    private String networkType;
    private Integer preloadCount;
    private Integer bufferMs;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getNetworkType() {
        return networkType;
    }

    public void setNetworkType(String networkType) {
        this.networkType = networkType;
    }

    public Integer getPreloadCount() {
        return preloadCount;
    }

    public void setPreloadCount(Integer preloadCount) {
        this.preloadCount = preloadCount;
    }

    public Integer getBufferMs() {
        return bufferMs;
    }

    public void setBufferMs(Integer bufferMs) {
        this.bufferMs = bufferMs;
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
}
