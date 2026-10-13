package io.github.gmjneko.gcfeed.modules.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 内部推荐查询；分页游标绑定 user_id 与归一化 scene。 */
public record QueryRecommendationDTO(
        @JsonProperty("user_id") Long userId,
        String scene,
        @JsonProperty("request_id") String requestId,
        String cursor,
        Integer limit
) {

}
