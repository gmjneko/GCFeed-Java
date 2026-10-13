package io.github.gmjneko.gcfeed.modules.recommendation.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * 冻结的推荐评分和解释；内部响应与快照使用同一结构。
 */
public record RecommendationCandidateVO(
        @JsonProperty("video_id") Long videoId,
        @JsonProperty("author_id") Long authorId,
        @JsonProperty("rank_score") double rankScore,
        double similarity,
        @JsonProperty("hot_score") long hotScore,
        @JsonProperty("freshness_score") double freshnessScore,
        String reason,
        @JsonProperty("published_at") @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime publishedAt
) {

}
