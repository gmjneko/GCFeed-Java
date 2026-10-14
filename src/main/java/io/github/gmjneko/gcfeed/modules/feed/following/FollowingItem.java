package io.github.gmjneko.gcfeed.modules.feed.following;

import java.time.LocalDateTime;

/** 关注流索引中的一条视频。排序使用发布时间和视频 ID。 */
public record FollowingItem(long videoId, long authorId, LocalDateTime publishedAt) {
}
