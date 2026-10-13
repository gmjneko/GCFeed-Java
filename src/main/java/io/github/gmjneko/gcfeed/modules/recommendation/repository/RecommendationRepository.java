package io.github.gmjneko.gcfeed.modules.recommendation.repository;

import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationCandidate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 推荐召回和实时资格查询的持久化边界。 */
public interface RecommendationRepository {

    /** 召回窗口内未曝光的上线视频，按热度、发布时间和 ID 降序读取至多 limit 条候选。 */
    List<RecommendationCandidate> recall(Long userId, LocalDateTime windowStart, int limit);

    /** 批量筛出仍上线、有发布时间且窗口内未曝光的候选 ID；空列表不查库。 */
    Set<Long> findEligibleIds(Long userId, List<Long> videoIds, LocalDateTime windowStart);

}
