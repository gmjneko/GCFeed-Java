package io.github.gmjneko.gcfeed.modules.feed.following;

import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import io.github.gmjneko.gcfeed.modules.mq.RabbitSettings;
import io.github.gmjneko.gcfeed.modules.relation.repository.RelationRepository;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.ScoredEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** 用 ZSET 保存关注流。任何 Redis 异常都交给调用方回退到数据库查询。 */
@Component
public class RedisFollowingIndex implements FollowingIndex {

    private static final Logger log = LoggerFactory.getLogger(RedisFollowingIndex.class);

    private final RedissonClient redis;
    private final RelationRepository relations;
    private final RabbitSettings settings;

    public RedisFollowingIndex(RedissonClient redis, RelationRepository relations, RabbitSettings settings) {
        this.redis = redis;
        this.relations = relations;
        this.settings = settings;
    }

    @Override
    public void addInbox(long authorId, List<Long> userIds, FollowingItem item) {
        if (userIds == null || item == null) {
            return;
        }
        for (Long userId : userIds) {
            if (userId != null && userId > 0) {
                add(inboxKey(userId), item, settings.inboxMaxLen());
            }
        }
    }

    @Override
    public void addOutbox(long authorId, FollowingItem item) {
        add(outboxKey(authorId), item, settings.outboxMaxLen());
    }

    @Override
    public void removeAuthorFromInbox(long userId, long authorId) {
        RScoredSortedSet<String> inbox = set(inboxKey(userId));
        Collection<String> members = inbox.readAll();
        List<String> matched = members.stream().filter(member -> FollowingMembers.belongsToAuthor(member, authorId)).toList();
        if (!matched.isEmpty()) {
            inbox.removeAll(matched);
        }
    }

    @Override
    public Optional<List<FollowingItem>> page(long viewerId, FeedCursor cursor, int limit) {
        try {
            RScoredSortedSet<String> inbox = set(inboxKey(viewerId));
            boolean inboxHasItems = inbox.size() > 0;
            List<Long> bigVAuthors = relations.bigVAuthorIds(viewerId, settings.bigVFollowerThreshold());
            List<RScoredSortedSet<String>> outboxes = new ArrayList<>();
            boolean outboxHasItems = false;
            for (Long authorId : bigVAuthors) {
                RScoredSortedSet<String> outbox = set(outboxKey(authorId));
                if (outbox.size() > 0) {
                    outboxHasItems = true;
                    outboxes.add(outbox);
                }
            }
            if (!inboxHasItems && !outboxHasItems) {
                return Optional.empty();
            }
            List<FollowingItem> merged = new ArrayList<>();
            if (inboxHasItems) {
                merged.addAll(read(inbox));
            }
            for (RScoredSortedSet<String> outbox : outboxes) {
                merged.addAll(read(outbox));
            }
            List<FollowingItem> ordered = merged.stream()
                    .filter(item -> newer(item, cursor))
                    .distinct()
                    .sorted(Comparator.comparing(FollowingItem::publishedAt).reversed()
                            .thenComparing(Comparator.comparingLong(FollowingItem::videoId).reversed()))
                    .limit(Math.max(limit, 0L))
                    .toList();
            return Optional.of(ordered);
        } catch (RuntimeException ex) {
            log.warn("关注流索引不可用，改为查询数据库 viewerId={}: {}", viewerId, ex.toString());
            return Optional.empty();
        }
    }

    private void add(String key, FollowingItem item, int maxLen) {
        RScoredSortedSet<String> set = set(key);
        set.add(FollowingMembers.score(item), FollowingMembers.member(item));
        int size = set.size();
        if (size > maxLen) {
            set.removeRangeByRank(0, size - maxLen - 1);
        }
        set.expire(Duration.ofSeconds(RedisKeyConstant.TTL_FEED_FOLLOWING_SECONDS));
    }

    private List<FollowingItem> read(RScoredSortedSet<String> set) {
        Collection<ScoredEntry<String>> entries = set.entryRangeReversed(0, settings.inboxMaxLen() - 1);
        List<FollowingItem> items = new ArrayList<>();
        for (ScoredEntry<String> entry : entries) {
            FollowingMembers.parse(entry.getValue()).ifPresent(items::add);
        }
        return items;
    }

    private boolean newer(FollowingItem item, FeedCursor cursor) {
        if (cursor == null) {
            return true;
        }
        int byTime = item.publishedAt().compareTo(cursor.publishedAt());
        return byTime < 0 || (byTime == 0 && item.videoId() < cursor.videoId());
    }

    private RScoredSortedSet<String> set(String key) {
        RScoredSortedSet<String> set = redis.getScoredSortedSet(key, StringCodec.INSTANCE);
        if (set == null) {
            throw new IllegalStateException("redis scored set is null");
        }
        return set;
    }

    private static String inboxKey(long userId) {
        return RedisKeyConstant.FEED_FOLLOWING_INBOX.formatted(userId);
    }

    private static String outboxKey(long authorId) {
        return RedisKeyConstant.FEED_FOLLOWING_AUTHOR.formatted(authorId);
    }
}
