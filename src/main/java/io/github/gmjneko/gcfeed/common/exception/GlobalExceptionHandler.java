package io.github.gmjneko.gcfeed.common.exception;

import io.github.gmjneko.gcfeed.common.api.ApiResponse;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 *
 * <p>把各类异常统一收敛成 {@link ApiResponse}，并让 HTTP 状态码与业务语义保持一致。
 * 这样前端既能靠 HTTP 状态码做通用处理，也能靠 {@code code} 做精确分支。
 *
 * <p>处理顺序说明：Spring 会优先匹配最具体的异常类型，
 * 因此 {@link BusinessException} 不会被兜底的 {@link Exception} 分支抢先处理。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 处理业务异常。
     *
     * <p>业务异常属于可预期的流程分支，只记录 warn 级日志，不打印完整堆栈，避免污染日志。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(
            BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("业务异常 uri={} code={} message={}",
                request.getRequestURI(), errorCode.getCode(), ex.getMessage());
        return build(errorCode, ex.getMessage());
    }

    /**
     * 处理 {@code @Valid} 修饰的请求体校验失败。
     *
     * <p>把所有字段错误拼成一行，便于开发期快速定位；生产环境可按需只返回首条。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("请求体校验失败 uri={} detail={}", request.getRequestURI(), detail);
        return build(ErrorCode.PARAM_ERROR, detail);
    }

    /**
     * 处理表单绑定校验失败。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<Void>> handleBindException(
            BindException ex, HttpServletRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败 uri={} detail={}", request.getRequestURI(), detail);
        return build(ErrorCode.PARAM_ERROR, detail);
    }

    /**
     * 处理方法参数级校验失败，例如 {@code @RequestParam} 上的约束注解。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
            ConstraintViolationException ex, HttpServletRequest request) {
        String detail = ex.getConstraintViolations().stream()
                .map(this::formatViolation)
                .collect(Collectors.joining("; "));
        log.warn("参数约束校验失败 uri={} detail={}", request.getRequestURI(), detail);
        return build(ErrorCode.PARAM_ERROR, detail);
    }

    /**
     * 处理请求体缺失或 JSON 格式错误。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        log.warn("请求体解析失败 uri={} message={}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.PARAM_ERROR, "request body is invalid");
    }

    /**
     * 处理路径变量或查询参数类型不匹配，例如把字母传给 {@code Long} 参数。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = "parameter '%s' is invalid".formatted(ex.getName());
        log.warn("参数类型不匹配 uri={} detail={}", request.getRequestURI(), detail);
        return build(ErrorCode.PARAM_ERROR, detail);
    }

    /**
     * 处理缺少必填查询参数。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        String detail = "parameter '%s' is required".formatted(ex.getParameterName());
        log.warn("缺少必填参数 uri={} detail={}", request.getRequestURI(), detail);
        return build(ErrorCode.PARAM_ERROR, detail);
    }

    /**
     * 处理上传文件超过配置上限。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        log.warn("上传文件过大 uri={} message={}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.UPLOAD_TOO_LARGE, ErrorCode.UPLOAD_TOO_LARGE.getMessage());
    }

    /** 处理 multipart 请求缺少 file 字段。 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingMultipartPart(
            MissingServletRequestPartException ex, HttpServletRequest request) {
        log.warn("上传文件缺失 uri={} part={}", request.getRequestURI(), ex.getRequestPartName());
        return build(ErrorCode.EMPTY_UPLOAD_FILE, ErrorCode.EMPTY_UPLOAD_FILE.getMessage());
    }

    /**
     * 处理请求方法不被支持。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        log.warn("请求方法不支持 uri={} method={}", request.getRequestURI(), ex.getMethod());
        return build(ErrorCode.METHOD_NOT_ALLOWED, ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    /**
     * 处理静态资源或路由不存在。
     *
     * <p>Spring Boot 4 对未匹配的请求会抛出该异常，这里显式转成 404，
     * 避免落到兜底分支返回 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFound(
            NoResourceFoundException ex, HttpServletRequest request) {
        log.warn("资源不存在 uri={}", request.getRequestURI());
        return build(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    /**
     * 兜底处理未预期异常。
     *
     * <p>该分支必须打印完整堆栈，否则线上问题无法定位；
     * 对外只返回通用文案，避免泄漏内部实现细节。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(
            Exception ex, HttpServletRequest request) {
        log.error("未预期异常 uri={}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    /**
     * 构造统一响应，HTTP 状态码取自错误码定义。
     */
    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode, String message) {
        HttpStatus status = HttpStatus.resolve(errorCode.getHttpStatus());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(ApiResponse.error(errorCode, message));
    }

    /**
     * 格式化字段校验错误为 {@code field: message}。
     */
    private String formatFieldError(FieldError fieldError) {
        return "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage());
    }

    /**
     * 格式化方法参数校验错误。
     */
    private String formatViolation(ConstraintViolation<?> violation) {
        return "%s: %s".formatted(violation.getPropertyPath(), violation.getMessage());
    }

}
