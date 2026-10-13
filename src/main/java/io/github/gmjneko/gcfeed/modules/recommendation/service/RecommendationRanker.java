package io.github.gmjneko.gcfeed.modules.recommendation.service;

import io.github.gmjneko.gcfeed.modules.exposure.entity.ViewEvent;
import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationCandidate;
import io.github.gmjneko.gcfeed.modules.recommendation.model.UserProfile;
import io.github.gmjneko.gcfeed.modules.recommendation.vo.RecommendationCandidateVO;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 纯计算组件：构建兴趣向量、稳定排序和有限轮次的页内作者打散。
 */
@Component
public class RecommendationRanker {

    /** 按有效观看样本加权平均后 L2 归一化，无有效方向时返回冷启动画像。 */
    public UserProfile profile(List<ViewEvent> events, Map<Long, double[]> vectors, int dimension) {
        double[] sum = new double[dimension];
        double totalWeight = 0;
        for (ViewEvent event : events) {
            if (!"play".equals(event.getEventType()) && !"complete".equals(event.getEventType())) continue;
            double[] vector = vectors.get(event.getVideoId());
            // 这里只检查向量有效性，平均时仍使用原向量，最后统一归一化画像。
            if (normalize(vector, dimension) == null) continue;
            double weight = eventWeight(event);
            for (int index = 0; index < dimension; index++) sum[index] += vector[index] * weight;
            totalWeight += weight;
        }
        if (totalWeight == 0) return UserProfile.empty();
        for (int index = 0; index < dimension; index++) sum[index] /= totalWeight;
        // 有样本也可能方向相互抵消；零范数不能当成有兴趣向量来使用匹配权重。
        double[] normalized = normalize(sum, dimension);
        return normalized == null ? UserProfile.empty() : new UserProfile(normalized);
    }

    /** 完播固定权重 3；play 随有效时长增长至 2，completed=true 时额外加 1。 */
    public double eventWeight(ViewEvent event) {
        if ("complete".equals(event.getEventType())) return 3;
        int watchMs = event.getWatchMs() == null ? 0 : Math.max(0, event.getWatchMs());
        // 必须使用浮点除法，否则不足 30 秒的有效观看时长不会贡献增量权重。
        return Math.min(1 + watchMs / 30000.0, 2) + (Boolean.TRUE.equals(event.getCompleted()) ? 1 : 0);
    }

    /** 使用统一时间锚点评分；无有效画像走冷启动，缺失候选向量仅令相似度为零。 */
    public List<RecommendationCandidateVO> rank(List<RecommendationCandidate> candidates,
                                                Map<Long, double[]> vectors, UserProfile profile,
                                                int dimension, LocalDateTime now) {
        double[] userVector = normalize(profile.vector(), dimension);
        List<RecommendationCandidateVO> ranked = new ArrayList<>(candidates.size());
        for (RecommendationCandidate candidate : candidates) {
            // 加权热度可超过单列 INT 范围，计算保持 long；未来发布时间的新鲜度最多为 1。
            long hot = candidate.likeCount() * 3L + candidate.commentCount() * 5L + candidate.favoriteCount() * 4L;
            double hours = Math.max(0, Duration.between(candidate.publishedAt(), now).toSeconds() / 3600.0);
            double freshness = 1 / (1 + hours / 72);
            double[] videoVector = normalize(vectors.get(candidate.videoId()), dimension);
            double similarity = 0;
            if (userVector != null && videoVector != null) {
                for (int index = 0; index < dimension; index++) similarity += userVector[index] * videoVector[index];
                // 仅钳制浮点误差，保留负相似度，让与用户兴趣相反的内容获得较低评分。
                similarity = Math.max(-1, Math.min(1, similarity));
            }
            // log1p 压缩热度跨度，同时保证零热度不会得到 log(0) 的负无穷。
            double heat = Math.log1p(Math.max(0, hot)) / 10;
            double score = userVector == null ? heat * .65 + freshness * .35
                    : similarity * .70 + heat * .20 + freshness * .10;
            String reason = userVector != null && similarity > .05 ? "interest_match" : hot > 0 ? "hot" : "fresh";
            ranked.add(new RecommendationCandidateVO(candidate.videoId(), candidate.authorId(), score,
                    similarity, hot, freshness, reason, candidate.publishedAt()));
        }
        // 同分时继续按发布时间和 ID 排序，避免输入顺序影响快照结果。
        ranked.sort(Comparator.comparingDouble(RecommendationCandidateVO::rankScore).reversed()
                .thenComparing(RecommendationCandidateVO::publishedAt, Comparator.reverseOrder())
                .thenComparing(RecommendationCandidateVO::videoId, Comparator.reverseOrder()));
        return List.copyOf(ranked);
    }

    /** 只调整选中页内的顺序，不改变候选成员，也不修改快照或游标位置。 */
    public List<RecommendationCandidateVO> diversify(List<RecommendationCandidateVO> page) {
        List<RecommendationCandidateVO> pending = new ArrayList<>(page);
        List<RecommendationCandidateVO> result = new ArrayList<>(page.size());
        while (!pending.isEmpty()) {
            int before = pending.size();
            Iterator<RecommendationCandidateVO> iterator = pending.iterator();
            while (iterator.hasNext()) {
                RecommendationCandidateVO candidate = iterator.next();
                if (result.isEmpty() || !result.getLast().authorId().equals(candidate.authorId())) {
                    result.add(candidate);
                    iterator.remove();
                }
            }
            // 一轮无法追加说明余项都与当前尾作者相同，放弃继续打散以保证终止。
            if (pending.size() == before) {
                result.addAll(pending);
                break;
            }
        }
        return List.copyOf(result);
    }

    /** 返回新的单位向量；维度错误、非有限值或零范数返回 null，由调用方按无效信号处理。 */
    public double[] normalize(double[] vector, int dimension) {
        if (vector == null || vector.length != dimension) return null;
        double norm = 0;
        for (double value : vector) {
            if (!Double.isFinite(value)) return null;
            // hypot 比直接累加平方更能避免较大有限分量导致的中间溢出。
            norm = Math.hypot(norm, value);
        }
        if (norm == 0 || !Double.isFinite(norm)) return null;
        // 原数组可能来自共享画像或视频向量，评分不能原地修改缓存内容。
        double[] result = vector.clone();
        for (int index = 0; index < dimension; index++) result[index] /= norm;
        return result;
    }

}
