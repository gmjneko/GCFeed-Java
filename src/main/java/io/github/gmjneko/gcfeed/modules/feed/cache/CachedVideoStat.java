package io.github.gmjneko.gcfeed.modules.feed.cache;

/** 已落库的互动计数。尚未刷库的点赞和收藏增量在读出后再叠加，不写进这份缓存。 */
public record CachedVideoStat(int likeCount, int commentCount, int favoriteCount) {
}
