package io.github.gmjneko.gcfeed.modules.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 映射 account 表；附加计数仅用于主页查询，不能通过账号更新写入。 */
@TableName("account")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String account;

    private String password;

    private String nickname;

    private String avatarUrl;

    private String bio;

    private Integer status;

    private String role;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private long followingCount;

    @TableField(exist = false)
    private long followerCount;

    @TableField(exist = false)
    private long workCount;

    /** 供 MyBatis 创建实体并回填数据库字段。 */
    public User() {
    }

    private User(Builder builder) {
        this.id = builder.id;
        this.account = builder.account;
        this.password = builder.password;
        this.nickname = builder.nickname;
        this.avatarUrl = builder.avatarUrl;
        this.bio = builder.bio;
        this.status = builder.status;
        this.role = builder.role;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.followingCount = builder.followingCount;
        this.followerCount = builder.followerCount;
        this.workCount = builder.workCount;
    }

    /** 创建实体构建器；业务默认值由具体用例显式指定。 */
    public static Builder builder() {
        return new Builder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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

    public long getFollowingCount() {
        return followingCount;
    }

    public void setFollowingCount(long followingCount) {
        this.followingCount = followingCount;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(long followerCount) {
        this.followerCount = followerCount;
    }

    public long getWorkCount() {
        return workCount;
    }

    public void setWorkCount(long workCount) {
        this.workCount = workCount;
    }

    /** 链式收集实体字段，每次 build 都创建独立的 User 实例。 */
    public static final class Builder {

        private Long id;
        private String account;
        private String password;
        private String nickname;
        private String avatarUrl;
        private String bio;
        private Integer status;
        private String role;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private long followingCount;
        private long followerCount;
        private long workCount;

        private Builder() {
        }

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder account(String account) {
            this.account = account;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder nickname(String nickname) {
            this.nickname = nickname;
            return this;
        }

        public Builder avatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
            return this;
        }

        public Builder bio(String bio) {
            this.bio = bio;
            return this;
        }

        public Builder status(Integer status) {
            this.status = status;
            return this;
        }

        public Builder role(String role) {
            this.role = role;
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

        public Builder followingCount(long followingCount) {
            this.followingCount = followingCount;
            return this;
        }

        public Builder followerCount(long followerCount) {
            this.followerCount = followerCount;
            return this;
        }

        public Builder workCount(long workCount) {
            this.workCount = workCount;
            return this;
        }

        public User build() {
            return new User(this);
        }

    }

}
