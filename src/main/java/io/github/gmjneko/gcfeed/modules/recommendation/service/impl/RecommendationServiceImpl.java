package io.github.gmjneko.gcfeed.modules.recommendation.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.embedding.repository.VideoEmbeddingRepository;
import io.github.gmjneko.gcfeed.modules.embedding.service.Vectorizer;
import io.github.gmjneko.gcfeed.modules.exposure.dto.BatchExposureDTO;
import io.github.gmjneko.gcfeed.modules.exposure.entity.Exposure;
import io.github.gmjneko.gcfeed.modules.exposure.model.ExposureInputNormalizer;
import io.github.gmjneko.gcfeed.modules.exposure.repository.ExposureRepository;
import io.github.gmjneko.gcfeed.modules.recommendation.cache.RecommendationCache;
import io.github.gmjneko.gcfeed.modules.recommendation.dto.QueryRecommendationDTO;
import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationCursor;
import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationProperties;
import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationSnapshot;
import io.github.gmjneko.gcfeed.modules.recommendation.model.UserProfile;
import io.github.gmjneko.gcfeed.modules.recommendation.repository.RecommendationRepository;
import io.github.gmjneko.gcfeed.modules.recommendation.service.RecommendationRanker;
import io.github.gmjneko.gcfeed.modules.recommendation.service.RecommendationService;
import io.github.gmjneko.gcfeed.modules.recommendation.vo.ExposureDecisionPageVO;
import io.github.gmjneko.gcfeed.modules.recommendation.vo.ExposureDecisionVO;
import io.github.gmjneko.gcfeed.modules.recommendation.vo.RecommendationCandidateVO;
import io.github.gmjneko.gcfeed.modules.recommendation.vo.RecommendationPageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 编排召回、画像缓存和快照分页；只读业务不建立长数据库事务。
 */
@Service
public class RecommendationServiceImpl implements RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationServiceImpl.class);
    private final RecommendationRepository repository;
    private final ExposureRepository exposures;
    private final VideoEmbeddingRepository embeddings;
    private final Vectorizer vectorizer;
    private final RecommendationCache cache;
    private final RecommendationRanker ranker;
    private final RecommendationProperties properties;
    private final Clock clock;
    private final JsonMapper json;

    public RecommendationServiceImpl(RecommendationRepository repository, ExposureRepository exposures,
                                     VideoEmbeddingRepository embeddings, Vectorizer vectorizer,
                                     RecommendationCache cache, RecommendationRanker ranker,
                                     RecommendationProperties properties, Clock clock, JsonMapper json) {
        this.repository = repository;
        this.exposures = exposures;
        this.embeddings = embeddings;
        this.vectorizer = vectorizer;
        this.cache = cache;
        this.ranker = ranker;
        this.properties = properties;
        this.clock = clock;
        this.json = json;
    }

    @Override
    public RecommendationPageVO recommend(QueryRecommendationDTO dto) {
        if (dto == null) throw new BusinessException(ErrorCode.PARAM_ERROR);
        ExposureInputNormalizer.userId(dto.userId());
        String scene = ExposureInputNormalizer.scene(dto.scene());
        String requestId = ExposureInputNormalizer.requestId(dto.requestId());
        int limit = dto.limit() == null ? CommonConstant.FEED_DEFAULT_PAGE_SIZE : dto.limit();
        if (limit <= 0) throw new BusinessException(ErrorCode.INVALID_LIMIT);
        limit = Math.min(limit, CommonConstant.MAX_PAGE_SIZE);
        // 参数与游标先校验，不能把非法续页当成新首屏；同次查询共用时间锚点计算窗口和新鲜度。
        RecommendationCursor cursor = RecommendationCursor.decode(dto.cursor(), json);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        try {
            String id;
            int offset;
            RecommendationSnapshot snapshot;
            if (cursor == null) {
                // 池大小不随 limit 改变，后续调整页大小仍在同一份冻结排名上推进。
                var candidates = repository.recall(dto.userId(), now.minusDays(properties.exposureWindowDays()),
                        properties.poolLimit());
                if (candidates.isEmpty()) return page(dto.userId(), scene, requestId, List.of(), "", false);
                var vectors = embeddings.findVectors(candidates.stream().map(c -> c.videoId()).toList(), vectorizer.model());
                var ranked = ranker.rank(candidates, vectors, profile(dto.userId(), now), vectorizer.dimension(), now);
                snapshot = new RecommendationSnapshot(dto.userId(), scene, vectorizer.model(), vectorizer.dimension(), now, ranked);
                id = UUID.randomUUID().toString();
                // 必须先保存快照再返回游标，否则首屏成功也无法保证下一页可读。
                cache.saveSnapshot(id, snapshot);
                offset = 0;
            } else {
                id = cursor.snapshotId();
                offset = cursor.offset();
                snapshot = cache.findSnapshot(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.RECOMMENDATION_SESSION_EXPIRED));
                // Base64 只编码位置，不能提供鉴权；用户和场景必须与服务端快照再次核对。
                if (!snapshot.userId().equals(dto.userId()) || !snapshot.scene().equals(scene)
                        || offset > snapshot.candidates().size()) throw new BusinessException(ErrorCode.INVALID_CURSOR);
                // 模型空间改变后旧评分不可沿用，要求新建快照，不在续页中重新排序。
                if (!snapshot.model().equals(vectorizer.model()) || snapshot.dimension() != vectorizer.dimension()) {
                    throw new BusinessException(ErrorCode.RECOMMENDATION_SESSION_EXPIRED);
                }
            }
            // 快照只冻结评分与顺序，不缓存资格：翻页期间的下架及新曝光应立即生效。
            var remaining = snapshot.candidates().subList(offset, snapshot.candidates().size());
            var eligible = repository.findEligibleIds(dto.userId(), remaining.stream().map(c -> c.videoId()).toList(),
                    now.minusDays(properties.exposureWindowDays()));
            List<RecommendationCandidateVO> selected = new ArrayList<>(limit);
            int nextOffset = offset;
            boolean hasMore = false;
            for (int index = offset; index < snapshot.candidates().size(); index++) {
                var candidate = snapshot.candidates().get(index);
                if (!eligible.contains(candidate.videoId())) continue;
                // 找到页外第一条仍有效的候选才算有下一页，不能用原快照的剩余条数判断。
                if (selected.size() == limit) {
                    hasMore = true;
                    break;
                }
                selected.add(candidate);
                nextOffset = index + 1;
            }
            // 偏移推进到打散前最后选中条目的下一位置；打散后的尾项可能来自页中间。
            String nextCursor = selected.isEmpty() ? "" : new RecommendationCursor(1, id, nextOffset).encode(json);
            return page(dto.userId(), scene, requestId, ranker.diversify(selected), nextCursor, hasMore);
        } catch (BusinessException ex) {
            // 保留参数错误和 410 刷新信号；存储异常由下一分支统一包装并保留底层原因。
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.LOAD_RECOMMENDATION_FAILED, ex);
        }
    }

    // 画像是可重建的派生缓存，允许回源；快照承载分页位置，读取失败不能换成另一份排名。
    private UserProfile profile(Long userId, LocalDateTime now) {
        try {
            var cached = cache.findProfile(userId, vectorizer.model(), vectorizer.dimension());
            if (cached.isPresent()) {
                double[] vector = cached.get().vector();
                // 空数组是有效的冷启动缓存；否则新用户每次查询都会重新扫描观看流水。
                if (vector != null && (vector.length == 0 || ranker.normalize(vector, vectorizer.dimension()) != null)) {
                    return cached.get();
                }
            }
        } catch (Exception ex) {
            log.warn("读取用户画像缓存失败，回源计算，userId={}", userId, ex);
        }
        var events = exposures.findInterestEvents(userId, now.minusDays(properties.profileWindowDays()),
                properties.profileSampleLimit());
        // 向量只需按视频去重读取，流水保留全部样本，重复观看仍独立贡献画像权重。
        var vectors = embeddings.findVectors(events.stream().map(e -> e.getVideoId()).distinct().toList(), vectorizer.model());
        UserProfile profile = ranker.profile(events, vectors, vectorizer.dimension());
        try {
            // 画像按固定 TTL 更新，观看上报不主动失效，新增兴趣允许在缓存过期后生效。
            cache.saveProfile(userId, vectorizer.model(), vectorizer.dimension(), profile);
        } catch (Exception ex) {
            log.warn("写入用户画像缓存失败，继续返回数据库计算结果，userId={}", userId, ex);
        }
        return profile;
    }

    @Override
    public ExposureDecisionPageVO exposureDecisions(BatchExposureDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        ExposureInputNormalizer.userId(dto.userId());
        String scene = ExposureInputNormalizer.scene(dto.scene());
        String requestId = ExposureInputNormalizer.requestId(dto.requestId());
        List<Long> ids = ExposureInputNormalizer.videoIds(dto.videoIds());
        try {
            // scene 只描述信号来源，不切分曝光窗口，避免跨场景反复推荐同一个视频。
            Map<Long, Exposure> recent = exposures.findRecent(dto.userId(), ids,
                    LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS).minusDays(properties.exposureWindowDays()));
            var decisions = ids.stream().map(id -> {
                Exposure exposure = recent.get(id);
                return new ExposureDecisionVO(id, exposure == null, exposure == null ? "fresh" : "recently_exposed",
                        exposure == null ? null : exposure.getLastExposedAt());
            }).toList();
            return new ExposureDecisionPageVO(dto.userId(), scene, requestId, decisions);
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.LOAD_RECOMMENDATION_FAILED, ex);
        }
    }

    private RecommendationPageVO page(Long userId, String scene, String requestId,
                                      List<RecommendationCandidateVO> candidates, String cursor, boolean hasMore) {
        return new RecommendationPageVO(userId, scene, requestId, candidates, cursor, hasMore);
    }

}
