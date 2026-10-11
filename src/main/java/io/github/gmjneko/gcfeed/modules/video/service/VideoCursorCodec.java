package io.github.gmjneko.gcfeed.modules.video.service;

import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursor;
import io.github.gmjneko.gcfeed.common.pagination.TimeIdCursorCodec;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoCursor;

/** 视频作品列表的类型适配，编码格式由公共分页工具维护。 */
public final class VideoCursorCodec {

    private VideoCursorCodec() {
    }

    public static String encode(VideoCursor cursor) {
        return TimeIdCursorCodec.encode(new TimeIdCursor(cursor.publishedAt(), cursor.videoId()));
    }

    public static VideoCursor decode(String encoded) {
        TimeIdCursor cursor = TimeIdCursorCodec.decode(encoded);
        return cursor == null ? null : new VideoCursor(cursor.time(), cursor.id());
    }
}
