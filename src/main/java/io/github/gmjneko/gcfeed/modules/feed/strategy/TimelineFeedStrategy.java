package io.github.gmjneko.gcfeed.modules.feed.strategy;

import io.github.gmjneko.gcfeed.modules.feed.cache.CachedTimelinePage;
import io.github.gmjneko.gcfeed.modules.feed.cache.FeedCache;
import io.github.gmjneko.gcfeed.modules.feed.cache.FeedCacheKeys;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedScene;
import io.github.gmjneko.gcfeed.modules.feed.repository.FeedRepository;
import io.github.gmjneko.gcfeed.modules.feed.support.SingleFlight;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedPageVO;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 全站时间线：全部上线视频按发布时间、ID 倒序，匿名可看。
 * 页缓存只存视频 ID、作者和发布时间（不含标题和计数——它们的变化频率不同，
 * 分别由卡片缓存 15 分钟、计数缓存 15 秒承担），第一页约 6 秒换一版。
 */
@Component
public class TimelineFeedStrategy implements FeedStrategy {

    private final FeedRepository feedRepository;
    private final FeedCache feedCache;
    private final FeedCardRenderer renderer;
    /** 合并同一页键在本进程内的并发回源：第一个请求查库，其余等待同一次结果。 */
    private final SingleFlight<TimelineLoad> timelineLoads = new SingleFlight<>();

    public TimelineFeedStrategy(FeedRepository feedRepository, FeedCache feedCache, FeedCardRenderer renderer) {
        this.feedRepository = feedRepository;
        this.feedCache = feedCache;
        this.renderer = renderer;
    }

    @Override
    public FeedScene scene() {
        return FeedScene.TIMELINE;
    }

    @Override
    public boolean requiresLogin() {
        return false;
    }

    @Override
    public FeedPageVO list(FeedQuery query) {
        FeedCursor position = FeedCursor.decode(query.cursor());
        // 页键含 limit 和游标哈希：不同 limit 的页各自缓存，长游标做 SHA-1 防止键过长。
        String key = FeedCacheKeys.pageKey(FeedScene.TIMELINE.getValue(), query.cursor(), query.limit());
        // 双重检查：SingleFlight 阻塞期间别的请求可能已经把这一页写进缓存，
        // 所以进 SingleFlight 后再读一次，避免重复查库。
        TimelineLoad loaded = feedCache.findPage(key)
                .map(page -> new TimelineLoad(page, Map.of()))
                .orElseGet(() -> timelineLoads.call(key, () -> feedCache.findPage(key)
                        .map(page -> new TimelineLoad(page, Map.of()))
                        .orElseGet(() -> loadTimeline(position, query.limit(), key, query.cursor()))));
        if (loaded.page().items().isEmpty()) {
            // 空结果合法：没有上线视频时返回空页而不是错误。
            return new FeedPageVO(FeedScene.TIMELINE.getValue(), List.of(), "", false);
        }
        List<PageSlot> slots = loaded.page().items().stream()
                .map(item -> new PageSlot(item.videoId(), item.authorId(), item.publishedAt()))
                .toList();
        return new FeedPageVO(FeedScene.TIMELINE.getValue(), renderer.render(slots, query.viewerId(), loaded.known()),
                loaded.page().nextCursor(), loaded.page().hasMore());
    }

    /** 缓存未命中的回源：查库、截断多取的一条、写回缓存并带上本次已查出的视频。 */
    private TimelineLoad loadTimeline(FeedCursor position, int limit, String key, String cursor) {
        List<Video> videos = feedRepository.findTimeline(position, limit);
        // Repository 最多返回 limit + 1 条，多出的一条只用来判断 has_more。
        boolean hasMore = videos.size() > limit;
        List<Video> page = hasMore ? videos.subList(0, limit) : videos;
        // 下一页游标取本页最后一条（时间, ID），同刻发布的靠 ID 打破平局。
        String next = page.isEmpty() ? "" : new FeedCursor(page.getLast().getPublishedAt(), page.getLast().getId()).encode();
        CachedTimelinePage cached = new CachedTimelinePage(page.stream()
                .map(video -> new CachedTimelinePage.CachedTimelineItem(video.getId(), video.getAuthorId(), video.getPublishedAt()))
                .toList(), next, hasMore);
        // TTL 由 FeedCacheKeys 按“第一页短、翻页长 + 固定比例抖动”决定，写失败只打日志。
        feedCache.savePage(key, cached, FeedCacheKeys.pageTtl(cursor, key));
        return new TimelineLoad(cached, renderer.index(page));
    }

    /** 一次回源的产物：缓存页 + 本页已查出的视频（render 优先复用，避免再查库）。 */
    private record TimelineLoad(CachedTimelinePage page, Map<Long, Video> known) {
    }
}
