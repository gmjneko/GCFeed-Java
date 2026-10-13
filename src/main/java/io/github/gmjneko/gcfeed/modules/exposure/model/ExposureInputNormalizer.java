package io.github.gmjneko.gcfeed.modules.exposure.model;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 公共观看接口和内部曝光接口共用清洗规则，场景不受 FeedScene 限制。
 */
public final class ExposureInputNormalizer {

    private ExposureInputNormalizer() {
    }

    public static String scene(String value) {
        // 先 strip 和固定 Locale 转小写再限长；按 Unicode 字符计数，不把代理对算作两个字符。
        String scene = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (scene.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_SCENE);
        }
        if (scene.codePointCount(0, scene.length()) > 32) {
            throw new BusinessException(ErrorCode.SCENE_TOO_LONG);
        }
        return scene;
    }

    public static String requestId(String value) {
        String id = value == null ? "" : value.strip();
        if (id.codePointCount(0, id.length()) > 64) {
            throw new BusinessException(ErrorCode.REQUEST_ID_TOO_LONG);
        }
        // 将未提供和空白统一为 NULL，持久化和响应字段省略遵循同一语义。
        return id.isEmpty() ? null : id;
    }

    public static void userId(Long id) {
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.INVALID_USER_ID);
        }
    }

    public static List<Long> videoIds(List<Long> ids) {
        if (ids == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new BusinessException(ErrorCode.INVALID_VIDEO_ID);
            }
        }
        // 仅去重本次输入并保留首次顺序；request_id 不承担跨请求幂等。
        return List.copyOf(new LinkedHashSet<>(ids));
    }

}
