package io.github.gmjneko.gcfeed.modules.recommendation.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 内部推荐分页结果；请求标识只作链路关联，不参与幂等或排序。
 */
public record RecommendationPageVO(
        @JsonProperty("user_id") Long userId,
        String scene,
        @JsonProperty("request_id") @JsonInclude(JsonInclude.Include.NON_NULL) String requestId,
        List<RecommendationCandidateVO> candidates,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {

}
