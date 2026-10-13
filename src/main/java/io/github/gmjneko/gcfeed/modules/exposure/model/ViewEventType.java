package io.github.gmjneko.gcfeed.modules.exposure.model;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;

import java.util.Locale;

/** 四种观看信号；只有 EXPOSED 维护推荐去重所用的曝光聚合。 */
public enum ViewEventType {
    EXPOSED("exposed"), PLAY("play"), COMPLETE("complete"), SKIP("skip");

    private final String value;

    ViewEventType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    /** 固定 Locale 归一化；缺失、空白及未知类型都映射为 INVALID_EVENT_TYPE。 */
    public static ViewEventType normalize(String eventType) {
        String value = eventType == null ? "" : eventType.strip().toLowerCase(Locale.ROOT);
        for (ViewEventType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new BusinessException(ErrorCode.INVALID_EVENT_TYPE);
    }

}
