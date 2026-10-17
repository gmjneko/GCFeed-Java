package io.github.gmjneko.gcfeed.modules.feed.strategy;

import java.time.LocalDateTime;

/**
 * 渲染槽位：页缓存和关注流索引里轻量存放的三元组。卡片组装只需要 videoId，
 * authorId 与 publishedAt 仅在卡片缓存未命中、又查不到库时作兜底展示，
 * 例如热榜 Redis 路径只有 ID（null, null）。
 */
record PageSlot(long videoId, Long authorId, LocalDateTime publishedAt) {
}
