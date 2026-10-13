package io.github.gmjneko.gcfeed.modules.recommendation.repository;

import io.github.gmjneko.gcfeed.modules.recommendation.mapper.RecommendationMapper;
import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationCandidate;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/** 批量 JOIN 查询，空候选不访问数据库。 */
@Repository
public class RecommendationRepositoryImpl implements RecommendationRepository {

    private final RecommendationMapper mapper;

    public RecommendationRepositoryImpl(RecommendationMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<RecommendationCandidate> recall(Long userId, LocalDateTime windowStart, int limit) {
        return mapper.recall(userId, windowStart, limit);
    }

    @Override
    public Set<Long> findEligibleIds(Long userId, List<Long> videoIds, LocalDateTime windowStart) {
        return videoIds.isEmpty() ? Set.of() : Set.copyOf(mapper.findEligibleIds(userId, videoIds, windowStart));
    }

}
