package io.github.gmjneko.gcfeed.modules.interaction.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/** Feed 只读查询使用的互动行为映射，写入能力后续由互动模块实现。 */
@TableName("interaction_action")
public class InteractionAction {

    public static final String LIKE = "LIKE";
    public static final String FAVORITE = "FAVORITE";
    public static final int STATUS_ACTIVE = 1;

    @TableId
    private Long id;
    private Long userId;
    private Long videoId;
    private String actionType;
    private Integer status;
    private Integer deleted;

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

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
