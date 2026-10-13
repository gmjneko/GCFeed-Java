package io.github.gmjneko.gcfeed.modules.recommendation.cache;

import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationSnapshot;
import io.github.gmjneko.gcfeed.modules.recommendation.model.UserProfile;

import java.util.Optional;

/**
 * 隔离 Redis 存储与推荐编排；读取不会延长有效期。
 */
public interface RecommendationCache {

    /**
     * 读取按用户、模型和维度隔离的画像缓存；未命中可回源，空向量画像属于有效命中。
     */
    Optional<UserProfile> findProfile(Long userId, String model, int dimension);

    /**
     * 缓存用户画像并设置配置的有效期；无有效兴趣样本时也缓存空画像。
     */
    void saveProfile(Long userId, String model, int dimension, UserProfile profile);

    /**
     * 读取排序快照且不续期；仅过期或不存在返回 empty，存储或解码失败应抛出。
     */
    Optional<RecommendationSnapshot> findSnapshot(String id);

    /**
     * 保存候选评分及顺序的快照并设置配置的有效期，供后续分页复用。
     */
    void saveSnapshot(String id, RecommendationSnapshot snapshot);

}
