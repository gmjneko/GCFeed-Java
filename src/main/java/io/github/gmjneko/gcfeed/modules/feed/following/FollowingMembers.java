package io.github.gmjneko.gcfeed.modules.feed.following;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** 收件箱成员与分数。分数把发布时间放在高位，相同时间再按视频 ID 区分。 */
public final class FollowingMembers {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private FollowingMembers() {
    }

    public static double score(FollowingItem item) {
        long epoch = item.publishedAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        return epoch * 1_000_000d + (item.videoId() % 1_000_000);
    }

    public static String member(FollowingItem item) {
        String time = item.publishedAt().atZone(ZoneId.systemDefault()).format(TIMESTAMP);
        return item.videoId() + ":" + item.authorId() + ":" + time;
    }

    public static Optional<FollowingItem> parse(String member) {
        if (member == null) {
            return Optional.empty();
        }
        int first = member.indexOf(':');
        int second = member.indexOf(':', first + 1);
        if (first <= 0 || second <= first) {
            return Optional.empty();
        }
        try {
            long videoId = Long.parseLong(member.substring(0, first));
            long authorId = Long.parseLong(member.substring(first + 1, second));
            LocalDateTime publishedAt = java.time.OffsetDateTime.parse(member.substring(second + 1), TIMESTAMP)
                    .toLocalDateTime();
            if (videoId <= 0 || authorId <= 0 || publishedAt == null) {
                return Optional.empty();
            }
            return Optional.of(new FollowingItem(videoId, authorId, publishedAt));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public static boolean belongsToAuthor(String member, long authorId) {
        return parse(member).filter(item -> item.authorId() == authorId).isPresent();
    }
}
