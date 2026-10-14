package io.github.gmjneko.gcfeed.modules.feed.model;

import java.time.LocalDateTime;

/** 热榜降级到数据库时的三键位置：累计热度、发布时间、视频 ID，均为倒序。 */
public record HotScoreCursor(int hotScore, LocalDateTime publishedAt, long videoId) {
}
