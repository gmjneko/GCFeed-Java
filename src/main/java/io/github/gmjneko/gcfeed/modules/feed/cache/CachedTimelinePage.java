package io.github.gmjneko.gcfeed.modules.feed.cache;

import java.time.LocalDateTime;
import java.util.List;

/** 时间线页缓存只保存排序位置，不保存点赞、收藏或关注这些随用户变化的字段。 */
public record CachedTimelinePage(List<CachedTimelineItem> items, String nextCursor, boolean hasMore) {

    public CachedTimelinePage {
        items = List.copyOf(items);
    }

    public record CachedTimelineItem(long videoId, long authorId, LocalDateTime publishedAt) {
    }
}
