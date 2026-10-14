package io.github.gmjneko.gcfeed.modules.message.model;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;

import java.util.Locale;

/** 站内通知类型。大小写不敏感，落库统一为大写。 */
public enum MessageType {
    LIKE,
    COMMENT,
    FOLLOW,
    SYSTEM;

    public static MessageType normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_MESSAGE_TYPE);
        }
        try {
            return valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_MESSAGE_TYPE);
        }
    }
}
