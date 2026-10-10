package io.github.gmjneko.gcfeed.modules.user.vo;

import io.github.gmjneko.gcfeed.modules.user.entity.User;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 返回本人资料与统计；明确列出字段，避免把密码哈希序列化给客户端。 */
public record UserVO(
        Long id,
        String account,
        String nickname,
        @JsonProperty("avatar_url")
        String avatarUrl,
        String bio,
        Integer status,
        String role,
        @JsonProperty("following_count")
        long followingCount,
        @JsonProperty("follower_count")
        long followerCount,
        @JsonProperty("work_count")
        long workCount
) {

    public static UserVO from(User user) {
        return new UserVO(user.getId(), user.getAccount(), user.getNickname(), user.getAvatarUrl(),
                user.getBio(), user.getStatus(), user.getRole(), user.getFollowingCount(),
                user.getFollowerCount(), user.getWorkCount());
    }

}
