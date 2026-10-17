package io.github.gmjneko.gcfeed.modules.feed.strategy;

/**
 * 分发到场景策略的请求参数。字段由 FeedServiceImpl 归一化后传入：
 * cursor 保持原始字符串由各策略自行解码；limit 已收紧到 [1, 100]；
 * viewerId 对 requiresLogin 的策略保证非空，匿名场景可能为 null；
 * requestId 已过 ExposureInputNormalizer，仅推荐场景透传，
 * 不作为幂等键或排序依据。
 */
public record FeedQuery(String cursor, int limit, Long viewerId, String requestId) {
}
