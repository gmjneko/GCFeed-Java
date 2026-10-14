package io.github.gmjneko.gcfeed.modules.feed.service.impl;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.exposure.model.ExposureInputNormalizer;
import io.github.gmjneko.gcfeed.modules.feed.cache.CachedTimelinePage;
import io.github.gmjneko.gcfeed.modules.feed.cache.CachedVideoStat;
import io.github.gmjneko.gcfeed.modules.feed.cache.FeedCache;
import io.github.gmjneko.gcfeed.modules.feed.cache.FeedCacheKeys;
import io.github.gmjneko.gcfeed.modules.feed.cache.FeedCardCache;
import io.github.gmjneko.gcfeed.modules.feed.hot.HotRanking;
import io.github.gmjneko.gcfeed.modules.feed.hot.HotWindows;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedScene;
import io.github.gmjneko.gcfeed.modules.feed.model.HotCursor;
import io.github.gmjneko.gcfeed.modules.feed.model.HotScoreCursor;
import io.github.gmjneko.gcfeed.modules.feed.repository.FeedRepository;
import io.github.gmjneko.gcfeed.modules.feed.service.FeedService;
import io.github.gmjneko.gcfeed.modules.feed.support.SingleFlight;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedItemVO;
import io.github.gmjneko.gcfeed.modules.feed.vo.FeedPageVO;
import io.github.gmjneko.gcfeed.modules.interaction.entity.InteractionAction;
import io.github.gmjneko.gcfeed.modules.recommendation.dto.QueryRecommendationDTO;
import io.github.gmjneko.gcfeed.modules.recommendation.service.RecommendationService;
import io.github.gmjneko.gcfeed.modules.user.entity.User;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import io.github.gmjneko.gcfeed.modules.video.model.VideoStatField;
import io.github.gmjneko.gcfeed.modules.video.service.VideoStatDeltaBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 校验请求、按场景取页，再用卡片和计数缓存组装。点赞和关注状态每次按当前用户查询。 */
@Service
public class FeedServiceImpl implements FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedServiceImpl.class);

    private final FeedRepository feedRepository;
    private final RecommendationService recommendations;
    private final FeedCache feedCache;
    private final HotRanking hotRanking;
    private final VideoStatDeltaBuffer statBuffer;
    private final Clock clock;
    private final JsonMapper json;
    private final SingleFlight<TimelineLoad> timelineLoads = new SingleFlight<>();

    public FeedServiceImpl(FeedRepository feedRepository, RecommendationService recommendations, FeedCache feedCache,
                           HotRanking hotRanking, VideoStatDeltaBuffer statBuffer, Clock clock, JsonMapper json) {
        this.feedRepository = feedRepository;
        this.recommendations = recommendations;
        this.feedCache = feedCache;
        this.hotRanking = hotRanking;
        this.statBuffer = statBuffer;
        this.clock = clock;
        this.json = json;
    }

    @Override
    public FeedPageVO getFeed(String scene, String cursor, int limit, Long viewerId, String requestId) {
        FeedScene actualScene = FeedScene.normalize(scene);
        if ((actualScene == FeedScene.FOLLOWING || actualScene == FeedScene.RECOMMEND) && viewerId == null) {
            throw new BusinessException(ErrorCode.VIEWER_REQUIRED);
        }
        if (limit <= 0) {
            throw new BusinessException(ErrorCode.INVALID_LIMIT);
        }
        int actualLimit = Math.min(limit, CommonConstant.MAX_PAGE_SIZE);
        String actualRequestId = ExposureInputNormalizer.requestId(requestId);
        if (actualScene == FeedScene.RECOMMEND) {
            var result = recommendations.recommend(new QueryRecommendationDTO(viewerId, actualScene.getValue(),
                    actualRequestId, cursor, actualLimit));
            List<Video> page = feedRepository.findPublishedByIds(result.candidates().stream().map(c -> c.videoId()).toList());
            return new FeedPageVO(actualScene.getValue(), render(slots(page), viewerId, index(page)),
                    result.nextCursor(), result.hasMore());
        }
        if (actualScene == FeedScene.HOT) {
            return hot(cursor, actualLimit, viewerId);
        }
        if (actualScene == FeedScene.FOLLOWING) {
            FeedCursor position = FeedCursor.decode(cursor);
            List<Video> videos = feedRepository.findFollowing(viewerId, position, actualLimit);
            return timePage(FeedScene.FOLLOWING, videos, actualLimit, viewerId);
        }
        return timeline(cursor, actualLimit, viewerId);
    }

    private FeedPageVO timeline(String cursor, int limit, Long viewerId) {
        FeedCursor position = FeedCursor.decode(cursor);
        String key = FeedCacheKeys.pageKey(FeedScene.TIMELINE.getValue(), cursor, limit);
        TimelineLoad loaded = feedCache.findPage(key)
                .map(page -> new TimelineLoad(page, Map.of()))
                .orElseGet(() -> timelineLoads.call(key, () -> feedCache.findPage(key)
                        .map(page -> new TimelineLoad(page, Map.of()))
                        .orElseGet(() -> loadTimeline(position, limit, key, cursor))));
        if (loaded.page().items().isEmpty()) {
            return new FeedPageVO(FeedScene.TIMELINE.getValue(), List.of(), "", false);
        }
        List<PageSlot> slots = loaded.page().items().stream()
                .map(item -> new PageSlot(item.videoId(), item.authorId(), item.publishedAt()))
                .toList();
        return new FeedPageVO(FeedScene.TIMELINE.getValue(), render(slots, viewerId, loaded.known()),
                loaded.page().nextCursor(), loaded.page().hasMore());
    }

    private TimelineLoad loadTimeline(FeedCursor position, int limit, String key, String cursor) {
        List<Video> videos = feedRepository.findTimeline(position, limit);
        boolean hasMore = videos.size() > limit;
        List<Video> page = hasMore ? videos.subList(0, limit) : videos;
        String next = page.isEmpty() ? "" : new FeedCursor(page.getLast().getPublishedAt(), page.getLast().getId()).encode();
        CachedTimelinePage cached = new CachedTimelinePage(page.stream()
                .map(video -> new CachedTimelinePage.CachedTimelineItem(video.getId(), video.getAuthorId(), video.getPublishedAt()))
                .toList(), next, hasMore);
        feedCache.savePage(key, cached, FeedCacheKeys.pageTtl(cursor, key));
        return new TimelineLoad(cached, index(page));
    }

    private FeedPageVO hot(String cursor, int limit, Long viewerId) {
        HotCursor hotCursor = HotCursor.decode(cursor, json);
        if (hotCursor != null && hotCursor.kind() == HotCursor.Kind.SCORE) {
            if (hotRanking.available()) {
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
            log.warn("热榜降级到数据库查询");
            return hotFromDatabase(hotCursor.score(), limit, viewerId);
        }
        LocalDateTime windowEnd = hotCursor == null
                ? HotWindows.minute(clock.instant())
                : hotCursor.windowEnd().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        int offset = hotCursor == null ? 0 : hotCursor.offset();
        HotRanking.HotPage ranked = hotRanking.page(windowEnd, offset, limit + 1);
        if (!ranked.available()) {
            if (ranked.failure() != null) {
                log.warn("热榜降级到数据库查询", ranked.failure());
            } else {
                log.warn("热榜降级到数据库查询");
            }
            if (hotCursor != null && hotCursor.kind() == HotCursor.Kind.WINDOW) {
                throw new BusinessException(ErrorCode.INVALID_CURSOR);
            }
            return hotFromDatabase(null, limit, viewerId);
        }
        boolean hasMore = ranked.videoIds().size() > limit;
        List<Long> pageIds = hasMore ? ranked.videoIds().subList(0, limit) : ranked.videoIds();
        List<PageSlot> slots = pageIds.stream().map(id -> new PageSlot(id, null, null)).toList();
        int nextOffset = offset + pageIds.size();
        String next = pageIds.isEmpty() ? "" : HotCursor.encodeWindow(json, windowEnd, nextOffset);
        return new FeedPageVO(FeedScene.HOT.getValue(), render(slots, viewerId, Map.of()), next, hasMore);
    }

    private FeedPageVO hotFromDatabase(HotScoreCursor cursor, int limit, Long viewerId) {
        List<Video> videos = feedRepository.findHot(cursor, limit);
        return timePage(FeedScene.HOT, videos, limit, viewerId, true);
    }

    private FeedPageVO timePage(FeedScene scene, List<Video> videos, int limit, Long viewerId) {
        return timePage(scene, videos, limit, viewerId, false);
    }

    private FeedPageVO timePage(FeedScene scene, List<Video> videos, int limit, Long viewerId, boolean hotScore) {
        if (videos.isEmpty()) {
            return new FeedPageVO(scene.getValue(), List.of(), "", false);
        }
        boolean hasMore = videos.size() > limit;
        List<Video> page = hasMore ? videos.subList(0, limit) : videos;
        Video last = page.getLast();
        String next = hotScore
                ? HotCursor.encodeScore(json, new HotScoreCursor(valueOrZero(last.getHotScore()), last.getPublishedAt(), last.getId()))
                : new FeedCursor(last.getPublishedAt(), last.getId()).encode();
        return new FeedPageVO(scene.getValue(), render(slots(page), viewerId, index(page)), next, hasMore);
    }

    private List<FeedItemVO> render(List<PageSlot> slots, Long viewerId, Map<Long, Video> known) {
        if (slots.isEmpty()) {
            return List.of();
        }
        List<Long> videoIds = slots.stream().map(PageSlot::videoId).distinct().toList();
        Map<Long, FeedCardCache> cards = new HashMap<>(feedCache.findCards(videoIds));
        List<FeedCardCache> missingCards = new ArrayList<>();
        List<Video> loaded = new ArrayList<>();
        List<Long> unresolved = new ArrayList<>();
        for (Long videoId : videoIds) {
            if (cards.containsKey(videoId)) {
                continue;
            }
            Video video = known.get(videoId);
            if (video == null) {
                unresolved.add(videoId);
            } else {
                loaded.add(video);
            }
        }
        if (!unresolved.isEmpty()) {
            loaded.addAll(feedRepository.findPublishedByIds(unresolved));
        }
        if (!loaded.isEmpty()) {
            Map<Long, User> authors = feedRepository.findAuthors(loaded.stream().map(Video::getAuthorId).distinct().toList());
            for (Video video : loaded) {
                User author = authors.get(video.getAuthorId());
                FeedCardCache card = new FeedCardCache(video.getId(), video.getAuthorId(),
                        author == null ? null : author.getNickname(),
                        author == null ? null : author.getAvatarUrl(),
                        video.getTitle(), video.getDescription(), video.getMediaUrl(), video.getCoverUrl(),
                        video.getPublishedAt());
                cards.put(video.getId(), card);
                missingCards.add(card);
            }
            feedCache.saveCards(missingCards);
        }
        Map<Long, CachedVideoStat> stats = stats(videoIds);
        Set<Long> authorIds = new HashSet<>();
        for (PageSlot slot : slots) {
            FeedCardCache card = cards.get(slot.videoId());
            if (card != null) {
                authorIds.add(card.authorId());
            } else if (slot.authorId() != null) {
                authorIds.add(slot.authorId());
            }
        }
        Set<Long> followedAuthors = feedRepository.findFollowedAuthors(viewerId, List.copyOf(authorIds));
        Set<Long> liked = new HashSet<>();
        Set<Long> favorited = new HashSet<>();
        if (viewerId != null) {
            for (InteractionAction action : feedRepository.findViewerActions(viewerId, videoIds)) {
                if (InteractionAction.LIKE.equals(action.getActionType())) {
                    liked.add(action.getVideoId());
                } else if (InteractionAction.FAVORITE.equals(action.getActionType())) {
                    favorited.add(action.getVideoId());
                }
            }
        }
        List<FeedItemVO> items = new ArrayList<>(slots.size());
        for (PageSlot slot : slots) {
            FeedCardCache card = cards.get(slot.videoId());
            if (card == null) {
                continue;
            }
            CachedVideoStat stat = stats.get(slot.videoId());
            items.add(new FeedItemVO(card.videoId(), card.authorId(), card.authorNickname(), card.authorAvatarUrl(),
                    card.title(), card.description(), card.mediaUrl(), card.coverUrl(),
                    stat == null ? 0 : stat.likeCount(),
                    stat == null ? 0 : stat.commentCount(),
                    stat == null ? 0 : stat.favoriteCount(),
                    liked.contains(card.videoId()), favorited.contains(card.videoId()),
                    followedAuthors.contains(card.authorId()),
                    card.publishedAt() != null ? card.publishedAt() : slot.publishedAt()));
        }
        return items;
    }

    private Map<Long, CachedVideoStat> stats(List<Long> videoIds) {
        Map<Long, CachedVideoStat> cached = new HashMap<>(feedCache.findStats(videoIds));
        List<Long> missing = videoIds.stream().filter(id -> !cached.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            Map<Long, VideoStat> stored = feedRepository.findStats(missing);
            Map<Long, CachedVideoStat> fresh = new HashMap<>();
            for (Long videoId : missing) {
                VideoStat stat = stored.get(videoId);
                CachedVideoStat value = new CachedVideoStat(
                        stat == null ? 0 : valueOrZero(stat.getLikeCount()),
                        stat == null ? 0 : valueOrZero(stat.getCommentCount()),
                        stat == null ? 0 : valueOrZero(stat.getFavoriteCount()));
                fresh.put(videoId, value);
                cached.put(videoId, value);
            }
            feedCache.saveStats(fresh);
        }
        Map<Long, CachedVideoStat> merged = new HashMap<>();
        for (Long videoId : videoIds) {
            CachedVideoStat stat = cached.getOrDefault(videoId, new CachedVideoStat(0, 0, 0));
            Map<VideoStatField, Long> pending = pending(videoId);
            merged.put(videoId, new CachedVideoStat(
                    count(stat.likeCount(), pending.getOrDefault(VideoStatField.LIKE_COUNT, 0L)),
                    stat.commentCount(),
                    count(stat.favoriteCount(), pending.getOrDefault(VideoStatField.FAVORITE_COUNT, 0L))));
        }
        return merged;
    }

    private Map<VideoStatField, Long> pending(Long videoId) {
        try {
            return statBuffer.pending(videoId);
        } catch (RuntimeException ex) {
            log.warn("读取待刷库计数失败，Feed 只使用已落库计数 videoId={}: {}", videoId, ex.toString());
            return Map.of();
        }
    }

    private List<PageSlot> slots(List<Video> videos) {
        return videos.stream().map(video -> new PageSlot(video.getId(), video.getAuthorId(), video.getPublishedAt())).toList();
    }

    private Map<Long, Video> index(List<Video> videos) {
        Map<Long, Video> indexed = new HashMap<>();
        for (Video video : videos) {
            indexed.put(video.getId(), video);
        }
        return indexed;
    }

    private int count(int stored, long pending) {
        return (int) Math.max(0, stored + pending);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record PageSlot(long videoId, Long authorId, LocalDateTime publishedAt) {
    }

    private record TimelineLoad(CachedTimelinePage page, Map<Long, Video> known) {
    }
}
