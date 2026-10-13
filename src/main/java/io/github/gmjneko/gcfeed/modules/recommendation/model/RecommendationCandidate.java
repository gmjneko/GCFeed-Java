package io.github.gmjneko.gcfeed.modules.recommendation.model;

import java.time.LocalDateTime;

/** 召回 SQL 的只读投影，不加载视频详情和额外关联。 */
public record RecommendationCandidate(Long videoId, Long authorId, long likeCount, long commentCount,
                                      long favoriteCount, LocalDateTime publishedAt) {

}
