package io.github.gmjneko.gcfeed.modules.relation.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record FollowVO(
        @JsonProperty("user_id")
        Long userId,
        @JsonProperty("target_user_id")
        Long targetUserId,
        int status,
        boolean following,
        @JsonProperty("following_count")
        int followingCount,
        @JsonProperty("follower_count")
        int followerCount
) {
}
