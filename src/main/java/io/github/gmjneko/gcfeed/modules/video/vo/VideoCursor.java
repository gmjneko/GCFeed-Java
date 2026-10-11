package io.github.gmjneko.gcfeed.modules.video.vo;

import java.time.LocalDateTime;

/** 视频作品列表游标的解码结果，排序键为 published_at DESC、id DESC。 */
public record VideoCursor(LocalDateTime publishedAt, Long videoId) {
}
