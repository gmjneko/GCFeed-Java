package io.github.gmjneko.gcfeed.modules.feed.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Feed 读缓存。Redis 失败时方法返回空结果或忽略写入，调用方继续查数据库。 */
public interface FeedCache {

    Optional<CachedTimelinePage> findPage(String key);

    void savePage(String key, CachedTimelinePage page, Duration ttl);

    Map<Long, FeedCardCache> findCards(List<Long> videoIds);

    void saveCards(Collection<FeedCardCache> cards);

    Map<Long, CachedVideoStat> findStats(List<Long> videoIds);

    void saveStats(Map<Long, CachedVideoStat> stats);

    /** 计数已经写进数据库后删除缓存，避免旧基数盖住新值。 */
    void evictStat(Long videoId);
}
