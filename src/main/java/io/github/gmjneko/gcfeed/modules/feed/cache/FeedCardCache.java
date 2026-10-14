package io.github.gmjneko.gcfeed.modules.feed.cache;

import java.time.LocalDateTime;

/** 视频卡片里很少变化的展示字段。是否仍在热榜或时间线里，由当次页查询决定。 */
public record FeedCardCache(
        long videoId,
        long authorId,
        String authorNickname,
        String authorAvatarUrl,
        String title,
        String description,
        String mediaUrl,
        String coverUrl,
        LocalDateTime publishedAt
) {
}
