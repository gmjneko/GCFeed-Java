package io.github.gmjneko.gcfeed.common.exception;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;

/**
 * 业务异常。
 *
 * <p>Service 层在业务规则不满足时抛出该异常，由
 * {@code GlobalExceptionHandler} 统一转换成 {@code ApiResponse} 与对应 HTTP 状态码。
 *
 * <p>示例：
 * <pre>
 * throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
 * throw new BusinessException(ErrorCode.COMMENT_PERMISSION_DENIED, "只能删除自己的评论");
 * </pre>
 */
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码。 */
    private final ErrorCode errorCode;

    /**
     * 使用错误码的默认提示语构造异常。
     *
     * @param errorCode 业务错误码
     */
    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码并覆盖提示语构造异常。
     *
     * @param errorCode 业务错误码
     * @param message   自定义提示语，会覆盖错误码默认值
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 使用错误码并保留底层原因构造异常。
     *
     * @param errorCode 业务错误码
     * @param cause     原始异常，便于日志打印堆栈
     */
    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

}
