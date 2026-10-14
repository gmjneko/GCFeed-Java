package io.github.gmjneko.gcfeed.modules.feed.cache;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 测试用读缓存。命中后不再回源，用来区分页、卡片和计数是否真的被记住。 */
public class MemoryFeedCache implements FeedCache {

    private final Map<String, CachedTimelinePage> pages = new HashMap<>();
    private final Map<Long, FeedCardCache> cards = new HashMap<>();
    private final Map<Long, CachedVideoStat> stats = new HashMap<>();

    public void clear() {
        pages.clear();
        cards.clear();
        stats.clear();
    }

    @Override
    public Optional<CachedTimelinePage> findPage(String key) {
        return Optional.ofNullable(pages.get(key));
    }

    @Override
    public void savePage(String key, CachedTimelinePage page, Duration ttl) {
        pages.put(key, page);
    }

    @Override
    public Map<Long, FeedCardCache> findCards(List<Long> videoIds) {
        Map<Long, FeedCardCache> found = new HashMap<>();
        for (Long videoId : videoIds) {
            FeedCardCache card = cards.get(videoId);
            if (card != null) {
                found.put(videoId, card);
            }
        }
        return found;
    }

    @Override
    public void saveCards(Collection<FeedCardCache> cards) {
        for (FeedCardCache card : cards) {
            this.cards.put(card.videoId(), card);
        }
    }

    @Override
    public Map<Long, CachedVideoStat> findStats(List<Long> videoIds) {
        Map<Long, CachedVideoStat> found = new HashMap<>();
        for (Long videoId : videoIds) {
            CachedVideoStat stat = stats.get(videoId);
            if (stat != null) {
                found.put(videoId, stat);
            }
        }
        return found;
    }

    @Override
    public void saveStats(Map<Long, CachedVideoStat> stats) {
        this.stats.putAll(stats);
    }

    @Override
    public void evictStat(Long videoId) {
        stats.remove(videoId);
    }
}
