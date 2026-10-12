package io.github.gmjneko.gcfeed.modules.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 映射 user_relation_stat 表，主键即用户 ID。
 *
 * <p>注册时由本模块创建初始行；关注/取关的计数更新属于 relation 模块职责，
 * 模块实现后由其在事务内维护。统计行缺失时读取方按 0 处理。
 */
@TableName("user_relation_stat")
public class UserRelationStat {

    /** 主键为 user_id，由业务赋值而非数据库自增。 */
    @TableId(type = IdType.INPUT)
    private Long userId;

    private Integer followingCount;

    private Integer followerCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 供 MyBatis 创建实体并回填数据库字段。 */
    public UserRelationStat() {
    }

    private UserRelationStat(Builder builder) {
        this.userId = builder.userId;
        this.followingCount = builder.followingCount;
        this.followerCount = builder.followerCount;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
    }

    /** 创建实体构建器；业务默认值由具体用例显式指定。 */
    public static Builder builder() {
        return new Builder();
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getFollowingCount() {
        return followingCount;
    }

    public void setFollowingCount(Integer followingCount) {
        this.followingCount = followingCount;
    }

    public Integer getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(Integer followerCount) {
        this.followerCount = followerCount;
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

    /** 链式收集实体字段，每次 build 都创建独立的 UserRelationStat 实例。 */
    public static final class Builder {

        private Long userId;
        private Integer followingCount;
        private Integer followerCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        private Builder() {
        }

        public Builder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Builder followingCount(Integer followingCount) {
            this.followingCount = followingCount;
            return this;
        }

        public Builder followerCount(Integer followerCount) {
            this.followerCount = followerCount;
            return this;
        }

        public Builder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public UserRelationStat build() {
            return new UserRelationStat(this);
        }

    }

}
