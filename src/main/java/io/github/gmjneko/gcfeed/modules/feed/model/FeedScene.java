package io.github.gmjneko.gcfeed.modules.feed.model;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;

import java.util.Locale;

/** 当前开放全站时间线及数据库关注流。 */
public enum FeedScene {
    TIMELINE("timeline"), FOLLOWING("following");

    private final String value;

    FeedScene(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static FeedScene normalize(String scene) {
        // 使用固定 Locale，避免服务器地区设置影响场景名称的大小写转换。
        String value = scene == null ? "" : scene.strip().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || TIMELINE.value.equals(value)) {
            return TIMELINE;
        }
        if (FOLLOWING.value.equals(value)) return FOLLOWING;
        // 未实现的场景显式拒绝，避免调用方误以为拿到了对应场景的数据。
        throw new BusinessException(ErrorCode.UNSUPPORTED_SCENE);
    }
}
