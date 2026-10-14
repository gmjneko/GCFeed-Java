package io.github.gmjneko.gcfeed.modules.feed.following;

import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import io.github.gmjneko.gcfeed.modules.mq.RabbitSettings;
import io.github.gmjneko.gcfeed.modules.relation.repository.RelationRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 测试用关注流索引，规则与 Redis 实现一致：没有数据时返回 empty，让调用方查库。 */
public class MemoryFollowingIndex implements FollowingIndex {

    private final Map<Long, Map<Long, FollowingItem>> inboxes = new LinkedHashMap<>();
    private final Map<Long, Map<Long, FollowingItem>> outboxes = new LinkedHashMap<>();
    private final RelationRepository relations;
    private final RabbitSettings settings;

    public MemoryFollowingIndex(RelationRepository relations, RabbitSettings settings) {
        this.relations = relations;
        this.settings = settings;
    }

    public void clear() {
        inboxes.clear();
        outboxes.clear();
    }

    public int inboxSize(long userId) {
        return inboxes.getOrDefault(userId, Map.of()).size();
    }

    public int outboxSize(long authorId) {
        return outboxes.getOrDefault(authorId, Map.of()).size();
    }

    @Override
    public void addInbox(long authorId, List<Long> userIds, FollowingItem item) {
        for (Long userId : userIds) {
            if (userId != null && userId > 0) {
                trim(inboxes.computeIfAbsent(userId, ignored -> new LinkedHashMap<>()), item, settings.inboxMaxLen());
            }
        }
    }

    @Override
    public void addOutbox(long authorId, FollowingItem item) {
        trim(outboxes.computeIfAbsent(authorId, ignored -> new LinkedHashMap<>()), item, settings.outboxMaxLen());
    }

    @Override
    public void removeAuthorFromInbox(long userId, long authorId) {
        Map<Long, FollowingItem> inbox = inboxes.get(userId);
        if (inbox != null) {
            inbox.entrySet().removeIf(entry -> entry.getValue().authorId() == authorId);
        }
    }

    @Override
    public Optional<List<FollowingItem>> page(long viewerId, FeedCursor cursor, int limit) {
        Map<Long, FollowingItem> inbox = inboxes.getOrDefault(viewerId, Map.of());
        List<Map<Long, FollowingItem>> sources = new ArrayList<>();
        boolean outboxHit = false;
        for (Long authorId : relations.bigVAuthorIds(viewerId, settings.bigVFollowerThreshold())) {
            Map<Long, FollowingItem> outbox = outboxes.get(authorId);
            if (outbox != null && !outbox.isEmpty()) {
                outboxHit = true;
                sources.add(outbox);
            }
        }
        if (inbox.isEmpty() && !outboxHit) {
            return Optional.empty();
        }
        List<FollowingItem> merged = new ArrayList<>(inbox.values());
        for (Map<Long, FollowingItem> source : sources) {
            merged.addAll(source.values());
        }
        return Optional.of(merged.stream()
                .filter(item -> cursor == null || item.publishedAt().isBefore(cursor.publishedAt())
                        || (item.publishedAt().equals(cursor.publishedAt()) && item.videoId() < cursor.videoId()))
                .distinct()
                .sorted(Comparator.comparing(FollowingItem::publishedAt).reversed()
                        .thenComparing(Comparator.comparingLong(FollowingItem::videoId).reversed()))
                .limit(Math.max(limit, 0))
                .toList());
    }

    private void trim(Map<Long, FollowingItem> bucket, FollowingItem item, int maxLen) {
        bucket.put(item.videoId(), item);
        if (bucket.size() <= maxLen) {
            return;
        }
        bucket.entrySet().stream()
                .min(Comparator.comparing(entry -> entry.getValue().publishedAt()))
                .ifPresent(oldest -> bucket.remove(oldest.getKey()));
    }
}
