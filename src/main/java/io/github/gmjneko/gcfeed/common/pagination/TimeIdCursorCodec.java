package io.github.gmjneko.gcfeed.common.pagination;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

/** 复用已发布的视频游标格式：UTF-8 时间|ID，URL-safe Base64，无 padding。 */
public final class TimeIdCursorCodec {

    private TimeIdCursorCodec() {
    }

    public static String encode(TimeIdCursor cursor) {
        // 编码只接收服务端生成的有效位置，缺失排序键属于调用错误。
        if (cursor == null || cursor.time() == null || cursor.id() == null || cursor.id() <= 0) {
            throw new IllegalArgumentException("cursor requires a time and positive id");
        }
        // 保留完整时间精度与既有格式，不能用响应中截断到秒的展示时间替代。
        String raw = cursor.time() + "|" + cursor.id();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static TimeIdCursor decode(String encoded) {
        // 只有未提供位置才表示首页；非空但损坏的游标必须报错。
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            // 保留末尾空字段，使缺失 ID 的内容也能被后续校验明确拒绝。
            String[] parts = raw.split("\\|", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid cursor shape");
            }
            LocalDateTime time = LocalDateTime.parse(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                throw new IllegalArgumentException("invalid cursor id");
            }
            return new TimeIdCursor(time, id);
        } catch (RuntimeException ex) {
            // 将 Base64、时间格式和数值解析异常统一转换为对外的非法游标错误。
            throw new BusinessException(ErrorCode.INVALID_CURSOR);
        }
    }
}
