package io.github.gmjneko.gcfeed.modules.feed.following;

import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;

import java.util.List;
import java.util.Optional;

/** 关注流的 Redis 收件箱和发件箱。空 Optional 表示索引不存在或 Redis 不可用，调用方改查数据库。 */
public interface FollowingIndex {

    void addInbox(long authorId, List<Long> userIds, FollowingItem item);

    void addOutbox(long authorId, FollowingItem item);

    void removeAuthorFromInbox(long userId, long authorId);

    Optional<List<FollowingItem>> page(long viewerId, FeedCursor cursor, int limit);
}
