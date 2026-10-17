package io.github.gmjneko.gcfeed.modules.feed.strategy;

import io.github.gmjneko.gcfeed.modules.feed.following.FollowingIndex;
import io.github.gmjneko.gcfeed.modules.feed.following.FollowingItem;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedScene;
import io.github.gmjneko.gcfeed.modules.feed.repository.FeedRepository;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedPageVO;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 关注流：推拉混合，必须登录。普通作者发布时已推送到粉丝收件箱（推），
 * 大 V 只保留作者发件箱、读取时现拉（拉）。索引命中时不再回源数据库——
 * 这是已知取舍：收件箱只保留最近 1000 条，更早的作品在索引路径看不到，
 * 换来读取时不用扫描全部关注关系。
 */
@Component
public class FollowingFeedStrategy implements FeedStrategy {

    private final FollowingIndex followingIndex;
    private final FeedRepository feedRepository;
    private final FeedCardRenderer renderer;

    public FollowingFeedStrategy(FollowingIndex followingIndex, FeedRepository feedRepository, FeedCardRenderer renderer) {
        this.followingIndex = followingIndex;
        this.feedRepository = feedRepository;
        this.renderer = renderer;
    }

    @Override
    public FeedScene scene() {
        return FeedScene.FOLLOWING;
    }

    @Override
    public boolean requiresLogin() {
        return true;
    }

    @Override
    public FeedPageVO list(FeedQuery query) {
        FeedCursor position = FeedCursor.decode(query.cursor());
        // 索引为空 Optional 表示收件箱和发件箱都没有数据（或 Redis 失败），
        // 此时降级查库；索引命中（哪怕只有一条）就完全信索引，不回源补更老的视频。
        Optional<List<FollowingItem>> indexed = followingIndex.page(query.viewerId(), position, query.limit() + 1);
        if (indexed.isPresent()) {
            return followingFromIndex(indexed.get(), query.limit(), query.viewerId());
        }
        // 数据库路径能看到全部历史：有效关注 + 上线视频，不限于最近 1000 条。
        List<Video> videos = feedRepository.findFollowing(query.viewerId(), position, query.limit());
        return renderer.timePage(FeedScene.FOLLOWING, videos, query.limit(), query.viewerId());
    }

    /** 索引路径的装配：索引条目已含作者和发布时间，按它构造游标和槽位。 */
    private FeedPageVO followingFromIndex(List<FollowingItem> fetched, int limit, Long viewerId) {
        // 调用方多取了一条（limit + 1）用于判断 has_more。
        boolean hasMore = fetched.size() > limit;
        List<FollowingItem> page = hasMore ? fetched.subList(0, limit) : fetched;
        if (page.isEmpty()) {
            return new FeedPageVO(FeedScene.FOLLOWING.getValue(), List.of(), "", false);
        }
        // 批量补齐视频实体并交给 render 复用（known），避免渲染层重复回库。
        Map<Long, Video> known = renderer.index(feedRepository.findPublishedByIds(page.stream().map(FollowingItem::videoId).toList()));
        List<PageSlot> slots = page.stream()
                .map(item -> new PageSlot(item.videoId(), item.authorId(), item.publishedAt()))
                .toList();
        FollowingItem last = page.getLast();
        return new FeedPageVO(FeedScene.FOLLOWING.getValue(), renderer.render(slots, viewerId, known),
                new FeedCursor(last.publishedAt(), last.videoId()).encode(), hasMore);
    }
}
