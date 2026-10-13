package io.github.gmjneko.gcfeed.modules.recommendation.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 按首次出现顺序返回曝光决策，场景不改变去重窗口。
 */
public record ExposureDecisionPageVO(
        @JsonProperty("user_id") Long userId,
        String scene,
        @JsonProperty("request_id") @JsonInclude(JsonInclude.Include.NON_NULL) String requestId,
        List<ExposureDecisionVO> decisions
) {

}
