package io.github.gmjneko.gcfeed.modules.feed.model;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 热榜游标有两种互斥形态：Redis 窗口用 {@code window_end + offset}，数据库降级用累计热度三键。
 * 客户端把游标当不透明字符串；服务端靠字段组合判断该走哪条路径。
 */
public record HotCursor(Kind kind, LocalDateTime windowEnd, int offset, HotScoreCursor score) {

    public enum Kind {
        WINDOW, SCORE
    }

    public static String encodeWindow(JsonMapper json, LocalDateTime windowEnd, int offset) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("window_end", windowEnd.toString());
        body.put("offset", offset);
        return encode(json, body);
    }

    public static String encodeScore(JsonMapper json, HotScoreCursor cursor) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("hot_score", cursor.hotScore());
        body.put("published_at", cursor.publishedAt().toString());
        body.put("video_id", cursor.videoId());
        return encode(json, body);
    }

    /** 空字符串是首页。窗口字段优先于累计热度字段，避免两种位置被当成同一种翻页。 */
    public static HotCursor decode(String encoded, JsonMapper json) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            JsonNode node = json.readTree(Base64.getUrlDecoder().decode(encoded.strip()));
            if (!node.isObject()) {
                throw new IllegalArgumentException("hot cursor is not an object");
            }
            if (node.hasNonNull("window_end")) {
                if (!node.path("offset").isIntegralNumber() || node.path("offset").asInt() < 0) {
                    throw new IllegalArgumentException("hot window offset is invalid");
                }
                return new HotCursor(Kind.WINDOW, LocalDateTime.parse(node.path("window_end").asString()),
                        node.path("offset").asInt(), null);
            }
            if (!node.path("hot_score").isIntegralNumber() || !node.path("published_at").isString()
                    || !node.path("video_id").isIntegralNumber() || node.path("video_id").asLong() <= 0) {
                throw new IllegalArgumentException("hot score cursor is invalid");
            }
            HotScoreCursor score = new HotScoreCursor(node.path("hot_score").asInt(),
                    LocalDateTime.parse(node.path("published_at").asString()), node.path("video_id").asLong());
            return new HotCursor(Kind.SCORE, null, 0, score);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.INVALID_CURSOR, ex);
        }
    }

    private static String encode(JsonMapper json, Map<String, Object> body) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(body));
    }
}
