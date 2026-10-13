package io.github.gmjneko.gcfeed.modules.recommendation;

import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;
import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationCandidate;
import io.github.gmjneko.gcfeed.modules.recommendation.model.UserProfile;
import io.github.gmjneko.gcfeed.modules.recommendation.service.RecommendationRanker;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 数值边界、稳定排序与打散的独立测试，不依赖 Spring 或数据库。 */
class RecommendationRankerTest {

    private final RecommendationRanker ranker = new RecommendationRanker();
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 13, 12, 0);

    @Test
    void fractionalWatchWeightsAndCompletedBonus() {
        assertThat(ranker.eventWeight(event(1, "play", 15000, false))).isEqualTo(1.5);
        assertThat(ranker.eventWeight(event(1, "play", 90000, true))).isEqualTo(3);
        assertThat(ranker.eventWeight(event(1, "complete", 0, false))).isEqualTo(3);
        UserProfile profile = ranker.profile(List.of(event(1, "play", 15000, false), event(2, "complete", 0, true)),
                Map.of(1L, new double[]{1, 0}, 2L, new double[]{0, 1}), 2);
        assertThat(profile.vector()[0]).isCloseTo(1 / Math.sqrt(5), within(1e-12));
        assertThat(profile.vector()[1]).isCloseTo(2 / Math.sqrt(5), within(1e-12));
    }

    @Test
    void invalidVectorsAndCancellationAreColdStart() {
        for (double[] vector : List.of(new double[0], new double[]{0, 0}, new double[]{Double.NaN, 1},
                new double[]{Double.POSITIVE_INFINITY, 1}, new double[]{1})) {
            assertThat(ranker.normalize(vector, 2)).isNull();
            assertThat(ranker.profile(List.of(event(1, "play", 0, false)), Map.of(1L, vector), 2).vector()).isEmpty();
        }
        assertThat(ranker.profile(List.of(event(1, "play", 0, false), event(2, "play", 0, false)),
                Map.of(1L, new double[]{1, 0}, 2L, new double[]{-1, 0}), 2).vector()).isEmpty();
    }

    @Test
    void signedSimilarityWarmAndColdFormulaAndFutureFreshness() {
        var candidate = new RecommendationCandidate(1L, 1L, 2, 4, 3, now.minusHours(72));
        var warm = ranker.rank(List.of(candidate), Map.of(1L, new double[]{-1, 0}),
                new UserProfile(new double[]{1, 0}), 2, now).getFirst();
        assertThat(warm.hotScore()).isEqualTo(38);
        assertThat(warm.similarity()).isEqualTo(-1);
        assertThat(warm.freshnessScore()).isEqualTo(.5);
        assertThat(warm.rankScore()).isCloseTo(-.7 + Math.log1p(38) / 10 * .2 + .05, within(1e-12));
        assertThat(warm.reason()).isEqualTo("hot");
        var cold = ranker.rank(List.of(candidate), Map.of(), UserProfile.empty(), 2, now).getFirst();
        assertThat(cold.rankScore()).isCloseTo(Math.log1p(38) / 10 * .65 + .175, within(1e-12));
        var future = ranker.rank(List.of(new RecommendationCandidate(2L, 1L, 0, 0, 0, now.plusHours(1))),
                Map.of(), UserProfile.empty(), 2, now).getFirst();
        assertThat(future.freshnessScore()).isEqualTo(1);
        assertThat(future.reason()).isEqualTo("fresh");
        assertThat(ranker.rank(List.of(candidate), Map.of(1L, new double[]{1, 0}),
                new UserProfile(new double[]{1, 0}), 2, now).getFirst().reason()).isEqualTo("interest_match");
    }

    @Test
    void longHeatAndTieBreakersAndMissingCandidateVector() {
        var high = new RecommendationCandidate(9L, 1L, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, now);
        assertThat(ranker.rank(List.of(high), Map.of(), UserProfile.empty(), 2, now).getFirst().hotScore())
                .isEqualTo(Integer.MAX_VALUE * 12L);
        var ranked = ranker.rank(List.of(new RecommendationCandidate(1L, 1L, 0, 0, 0, now),
                new RecommendationCandidate(2L, 1L, 0, 0, 0, now)), Map.of(),
                new UserProfile(new double[]{1, 0}), 2, now);
        assertThat(ranked).extracting(c -> c.videoId()).containsExactly(2L, 1L);
        assertThat(ranked.getFirst().similarity()).isZero();
    }

    @Test
    void diversificationKeepsMembershipAndTerminatesWithOneAuthor() {
        var page = ranker.rank(List.of(new RecommendationCandidate(4L, 1L, 0, 0, 0, now),
                new RecommendationCandidate(3L, 1L, 0, 0, 0, now),
                new RecommendationCandidate(2L, 2L, 0, 0, 0, now),
                new RecommendationCandidate(1L, 1L, 0, 0, 0, now)), Map.of(), UserProfile.empty(), 2, now);
        assertThat(ranker.diversify(page)).extracting(c -> c.videoId()).containsExactly(4L, 2L, 1L, 3L);
        assertThat(ranker.diversify(List.of(page.get(0), page.get(1), page.get(3)))).hasSize(3);
    }

    private ViewEvent event(long id, String type, int ms, boolean completed) {
        return ViewEvent.builder().videoId(id).eventType(type).watchMs(ms).completed(completed).build();
    }

}
