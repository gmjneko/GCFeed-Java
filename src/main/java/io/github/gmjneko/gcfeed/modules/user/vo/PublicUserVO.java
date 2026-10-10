package io.github.gmjneko.gcfeed.modules.user.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 对外公开的主页资料，不包含账号、角色、状态和密码。 */
public record PublicUserVO(
        Long id, String nickname,
        @JsonProperty("avatar_url") String avatarUrl,
        String bio,
        @JsonProperty("following_count") long followingCount,
        @JsonProperty("follower_count") long followerCount,
        @JsonProperty("work_count") long workCount) {

    public static PublicUserVO from(UserVO user) {
        return new PublicUserVO(user.id(), user.nickname(), user.avatarUrl(), user.bio(),
                user.followingCount(), user.followerCount(), user.workCount());
    }

}
