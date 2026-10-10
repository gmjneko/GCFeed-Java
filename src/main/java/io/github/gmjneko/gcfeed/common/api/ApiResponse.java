package io.github.gmjneko.gcfeed.common.api;

/**
 * 统一响应体。
 *
 * <p>所有 HTTP 接口（除上传静态资源外）都返回该结构，前端只需按 {@code code} 判断成功与否。
 *
 * <pre>
 * {
 *   "code": 0,
 *   "message": "ok",
 *   "data": { ... }
 * }
 * </pre>
 *
 * <p>约定：
 * <ul>
 *   <li>{@code code == 0} 表示成功，此时 {@code data} 为业务数据，失败时为 {@code null}。</li>
 *   <li>{@code message} 成功固定为 {@code ok}；失败时为可读的错误原因。</li>
 *   <li>HTTP 状态码同时保持语义正确（400/401/403/404/409/500），便于网关、监控和日志统计。</li>
 * </ul>
 *
 * @param <T> 业务数据类型
 */
public class ApiResponse<T> {

    /** 成功码。 */
    public static final int SUCCESS_CODE = 0;

    /** 成功提示语。 */
    public static final String SUCCESS_MESSAGE = "ok";

    /** 业务错误码，0 表示成功。 */
    private int code;

    /** 提示信息。 */
    private String message;

    /** 业务数据，失败时为 null。 */
    private T data;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ==================== 成功 ====================

    /** 返回成功响应，无数据体。 */
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, null);
    }

    /** 返回成功响应，携带业务数据。 */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    /** 返回成功响应，自定义提示语。 */
    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(SUCCESS_CODE, message, data);
    }

    // ==================== 失败 ====================

    /** 返回失败响应，使用默认错误码 {@link ErrorCode#INTERNAL_ERROR}。 */
    public static <T> ApiResponse<T> error() {
        return error(ErrorCode.INTERNAL_ERROR);
    }

    /** 返回失败响应，使用指定错误码的默认提示语。 */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 返回失败响应，使用指定错误码并覆盖提示语。 */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(errorCode.getCode(), message, null);
    }

    /** 返回失败响应，使用自定义码与提示语。 */
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    /** 判断当前响应是否成功。 */
    public boolean isSuccess() {
        return this.code == SUCCESS_CODE;
    }

    // ==================== getter / setter ====================

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

}
