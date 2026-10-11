package io.github.gmjneko.gcfeed.modules.video.service;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoCursor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/** 编解码视频作品列表游标，避免把数据库排序键直接暴露在 URL 中。 */
public final class VideoCursorCodec {

    private static final String SEPARATOR = "|";

    private VideoCursorCodec() {
    }

    public static String encode(VideoCursor cursor) {
        String raw = cursor.publishedAt() + SEPARATOR + cursor.videoId();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static VideoCursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid cursor shape");
            }
            LocalDateTime publishedAt = LocalDateTime.parse(parts[0]);
            long videoId = Long.parseLong(parts[1]);
            if (videoId <= 0) {
                throw new IllegalArgumentException("invalid video id");
            }
            return new VideoCursor(publishedAt, videoId);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
