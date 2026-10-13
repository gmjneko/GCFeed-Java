package io.github.gmjneko.gcfeed.common.interceptor;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.constant.CommonConstant;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 只允许持有服务间令牌的调用方访问内部接口，不读取浏览器 JWT。 */
public class InternalTokenInterceptor implements HandlerInterceptor {

    private final byte[] expected;

    public InternalTokenInterceptor(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("内部令牌不能为空");
        this.expected = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String actual = request.getHeader(CommonConstant.INTERNAL_TOKEN_HEADER);
        // 以字节比较减少令牌内容带来的时序差异，缺失或错误都只返回统一的 401。
        if (actual == null || !MessageDigest.isEqual(expected, actual.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return true;
    }

}
