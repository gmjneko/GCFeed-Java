package io.github.gmjneko.gcfeed.modules.feed.model;

import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursor;
import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursorCodec;

import java.time.LocalDateTime;

/** Timeline 按 published_at DESC、id DESC 翻页的位置。 */
public record FeedCursor(LocalDateTime publishedAt, Long videoId) {

    public String encode() {
        return TimeIdCursorCodec.encode(new TimeIdCursor(publishedAt, videoId));
    }

    public static FeedCursor decode(String encoded) {
        TimeIdCursor cursor = TimeIdCursorCodec.decode(encoded);
        return cursor == null ? null : new FeedCursor(cursor.time(), cursor.id());
    }
}
