package io.github.gmjneko.gcfeed.modules.feed.following;

import io.github.gmjneko.gcfeed.modules.embedding.service.VideoPublishedEvent;
import io.github.gmjneko.gcfeed.modules.feed.cache.FeedCache;
import io.github.gmjneko.gcfeed.modules.feed.cache.FeedCardCache;
import io.github.gmjneko.gcfeed.modules.feed.cache.CachedVideoStat;
import io.github.gmjneko.gcfeed.modules.feed.repository.FeedRepository;
import io.github.gmjneko.gcfeed.modules.mq.RabbitSettings;
import io.github.gmjneko.gcfeed.modules.relation.repository.RelationRepository;
import io.github.gmjneko.gcfeed.modules.user.entity.User;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 发布成功后的关注流扇出。普通作者推收件箱，大 V 只维护自己的发件箱。 */
@Service
public class VideoFanoutService {

    private final RelationRepository relations;
    private final FollowingIndex index;
    private final FeedRepository feed;
    private final FeedCache cache;
    private final RabbitSettings settings;

    public VideoFanoutService(RelationRepository relations, FollowingIndex index, FeedRepository feed,
                              FeedCache cache, RabbitSettings settings) {
        this.relations = relations;
        this.index = index;
        this.feed = feed;
        this.cache = cache;
        this.settings = settings;
    }

    public void onPublished(VideoPublishedEvent event) {
        if (event == null || event.videoId() == null || event.videoId() <= 0
                || event.authorId() == null || event.authorId() <= 0) {
            return;
        }
        List<Video> videos = feed.findPublishedByIds(List.of(event.videoId()));
        if (videos.isEmpty() || videos.getFirst().getPublishedAt() == null) {
            return;
        }
        Video video = videos.getFirst();
        preheat(video);
        FollowingItem item = new FollowingItem(video.getId(), video.getAuthorId(), video.getPublishedAt());
        if (relations.followerCount(video.getAuthorId()) >= settings.bigVFollowerThreshold()) {
            index.addOutbox(video.getAuthorId(), item);
            return;
        }
        long cursor = 0;
        while (true) {
            List<Long> followers = relations.followerIdsAfter(video.getAuthorId(), cursor, settings.followerBatchSize());
            if (followers.isEmpty()) {
                return;
            }
            index.addInbox(video.getAuthorId(), followers, item);
            cursor = followers.getLast();
            if (followers.size() < settings.followerBatchSize()) {
                return;
            }
        }
    }

    private void preheat(Video video) {
        Map<Long, User> authors = feed.findAuthors(List.of(video.getAuthorId()));
        User author = authors.get(video.getAuthorId());
        cache.saveCards(List.of(new FeedCardCache(video.getId(), video.getAuthorId(),
                author == null ? null : author.getNickname(),
                author == null ? null : author.getAvatarUrl(),
                video.getTitle(), video.getDescription(), video.getMediaUrl(), video.getCoverUrl(),
                video.getPublishedAt())));
        Map<Long, VideoStat> stats = feed.findStats(List.of(video.getId()));
        VideoStat stat = stats.get(video.getId());
        cache.saveStats(Map.of(video.getId(), new CachedVideoStat(
                stat == null || stat.getLikeCount() == null ? 0 : stat.getLikeCount(),
                stat == null || stat.getCommentCount() == null ? 0 : stat.getCommentCount(),
                stat == null || stat.getFavoriteCount() == null ? 0 : stat.getFavoriteCount())));
    }
}
