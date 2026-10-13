package io.github.gmjneko.gcfeed.modules.recommendation.model;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.UUID;

/** 推荐游标只记录快照位置，与时间线双键游标独立。 */
public record RecommendationCursor(int version, String snapshotId, int offset) {

    public String encode(JsonMapper json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.writeValueAsBytes(this));
    }

    /** 空游标表示新查询；格式和位置类型在这里校验，身份及位置上限由快照服务校验。 */
    public static RecommendationCursor decode(String encoded, JsonMapper json) {
        if (encoded == null || encoded.isBlank()) return null;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded.strip());
            var node = json.readTree(bytes);
            // 先检查 JSON 字段类型，避免缺失/NULL 被反序列化成 int 的默认 0 而误命中首屏位置。
            if (!node.isObject() || !node.path("version").isIntegralNumber()
                    || !node.path("offset").isIntegralNumber() || !node.path("snapshotId").isString()) {
                throw new IllegalArgumentException("游标缺少有效位置字段");
            }
            RecommendationCursor cursor = json.readValue(bytes, RecommendationCursor.class);
            if (cursor.version != 1 || cursor.offset < 0
                    || !UUID.fromString(cursor.snapshotId).toString().equals(cursor.snapshotId)) {
                throw new IllegalArgumentException("非法推荐游标");
            }
            return cursor;
        } catch (Exception ex) {
            // 格式错误统一视为非法游标，不能静默回到首屏并重复下发已有候选。
            throw new BusinessException(ErrorCode.INVALID_CURSOR, ex);
        }
    }

}
