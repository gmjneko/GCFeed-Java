package io.github.gmjneko.gcfeed.modules.feed.strategy;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.feed.hot.HotRanking;
import io.github.gmjneko.gcfeed.modules.feed.hot.HotWindows;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedScene;
import io.github.gmjneko.gcfeed.modules.feed.model.HotCursor;
import io.github.gmjneko.gcfeed.modules.feed.model.HotScoreCursor;
import io.github.gmjneko.gcfeed.modules.feed.repository.FeedRepository;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedPageVO;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 热榜：近一小时热度榜，匿名可看。优先读 Redis 分钟桶合并出的窗口，
 * Redis 失败时降级按累计热度查库——注意两者排序不同：窗口是“近一小时”，
 * 降级后变成“历史总热度”，属于保命路径，不是同一份榜的延迟。
 */
@Component
public class HotFeedStrategy implements FeedStrategy {

    private static final Logger log = LoggerFactory.getLogger(HotFeedStrategy.class);

    private final HotRanking hotRanking;
    private final FeedRepository feedRepository;
    private final FeedCardRenderer renderer;
    private final Clock clock;
    private final JsonMapper json;

    public HotFeedStrategy(HotRanking hotRanking, FeedRepository feedRepository, FeedCardRenderer renderer,
                           Clock clock, JsonMapper json) {
        this.hotRanking = hotRanking;
        this.feedRepository = feedRepository;
        this.renderer = renderer;
        this.clock = clock;
        this.json = json;
    }

    @Override
    public FeedScene scene() {
        return FeedScene.HOT;
    }

    @Override
    public boolean requiresLogin() {
        return false;
    }

    @Override
    public FeedPageVO list(FeedQuery query) {
        return hot(query.cursor(), query.limit(), query.viewerId());
    }

    private FeedPageVO hot(String cursor, int limit, Long viewerId) {
        HotCursor hotCursor = HotCursor.decode(cursor, json);
        // 累计热度游标只在降级路径有效：Redis 正常时拿它翻页属于数据源错配，拒绝而不是
        // 悄悄换数据，避免同一页列表一半来自窗口、一半来自数据库。
        if (hotCursor != null && hotCursor.kind() == HotCursor.Kind.SCORE) {
            if (hotRanking.available()) {
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
            log.warn("热榜降级到数据库查询");
            return hotFromDatabase(hotCursor.score(), limit, viewerId);
        }
        // 第一页以当前 UTC 分钟为窗口结束；翻页沿用游标里的 window_end，
        // 保证翻到一半窗口滑走也不会重复或漏掉。
        LocalDateTime windowEnd = hotCursor == null
                ? HotWindows.minute(clock.instant())
                : hotCursor.windowEnd().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        int offset = hotCursor == null ? 0 : hotCursor.offset();
        // 多取一条（limit + 1）判断 has_more，与时间线同一套约定。
        HotRanking.HotPage ranked = hotRanking.page(windowEnd, offset, limit + 1);
        if (!ranked.available()) {
            // 空榜是合法结果，只有 Redis 抛异常才走降级，不能把空列表当成 Redis 坏了。
            if (ranked.failure() != null) {
                log.warn("热榜降级到数据库查询", ranked.failure());
            } else {
                log.warn("热榜降级到数据库查询");
            }
            // Redis 已失败时窗口游标没有意义（窗口读不回来），要求客户端从第一页重开。
            if (hotCursor != null && hotCursor.kind() == HotCursor.Kind.WINDOW) {
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
            return hotFromDatabase(null, limit, viewerId);
        }
        boolean hasMore = ranked.videoIds().size() > limit;
        List<Long> pageIds = hasMore ? ranked.videoIds().subList(0, limit) : ranked.videoIds();
        // 热榜只给出 ID 顺序，作者和发布时间交给渲染层兜底补全。
        List<PageSlot> slots = pageIds.stream().map(id -> new PageSlot(id, null, null)).toList();
        int nextOffset = offset + pageIds.size();
        String next = pageIds.isEmpty() ? "" : HotCursor.encodeWindow(json, windowEnd, nextOffset);
        return new FeedPageVO(FeedScene.HOT.getValue(), renderer.render(slots, viewerId, Map.of()), next, hasMore);
    }

    /** 降级路径：按累计热度（点赞*3 + 评论*5 + 收藏*4）倒序查库，游标带热度分。 */
    private FeedPageVO hotFromDatabase(HotScoreCursor cursor, int limit, Long viewerId) {
        List<Video> videos = feedRepository.findHot(cursor, limit);
        return renderer.timePage(FeedScene.HOT, videos, limit, viewerId, true);
    }
}
