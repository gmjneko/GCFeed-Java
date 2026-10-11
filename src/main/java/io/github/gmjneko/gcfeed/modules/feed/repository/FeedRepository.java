package io.github.gmjneko.gcfeed.modules.feed.repository;

import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import io.github.gmjneko.gcfeed.modules.interaction.entity.InteractionAction;
import io.github.gmjneko.gcfeed.modules.user.entity.User;
import io.github.gmjneko.gcfeed.modules.video.entity.Video;
import io.github.gmjneko.gcfeed.modules.video.entity.VideoStat;

import java.util.List;
import java.util.Map;

/** Feed 持久化边界：查询均为批量，不在 Service 中直接使用 Mapper。 */
public interface FeedRepository {
    /** 最多读取 limit + 1 条，额外一条供 Service 判断 has_more。 */
    List<Video> findTimeline(FeedCursor cursor, int limit);

    Map<Long, User> findAuthors(List<Long> authorIds);

    Map<Long, VideoStat> findStats(List<Long> videoIds);

    List<InteractionAction> findViewerActions(Long viewerId, List<Long> videoIds);
}
