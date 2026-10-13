package io.github.gmjneko.gcfeed.modules.recommendation.model;

import io.github.gmjneko.gcfeed.modules.recommendation.vo.RecommendationCandidateVO;
import java.time.LocalDateTime;
import java.util.List;

/** 一次浏览的不可变排名；曝光和上线状态仍在每页实时检查。 */
public record RecommendationSnapshot(Long userId, String scene, String model, int dimension,
                                     LocalDateTime rankedAt, List<RecommendationCandidateVO> candidates) {

    public RecommendationSnapshot {
        // 防止调用方在保存后重排原列表，offset 必须始终指向同一份候选顺序。
        candidates = List.copyOf(candidates);
    }

}
