package io.github.gmjneko.gcfeed.common.interceptor;

import io.github.gmjneko.gcfeed.common.annotation.OptionalAuth;
import io.github.gmjneko.gcfeed.common.annotation.RequireAuth;
import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.context.UserContext;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.common.jwt.JwtPayload;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.common.jwt.TokenBlacklist;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器。
 *
 * <p>根据 Handler 上的注解决定鉴权强度：
 * <ul>
 *   <li>{@link RequireAuth}：必须携带合法 token，否则 401。</li>
 *   <li>{@link OptionalAuth}：token 合法则写入身份，非法或缺失都放行。</li>
 *   <li>两者都没有：完全公开，但仍会清理上一请求可能残留的上下文。</li>
 * </ul>
 *
 * <p>方法级注解优先于类级注解，因此一个 Controller 可以整体要求登录，
 * 再对个别公开接口标注 {@link OptionalAuth} 放开。
 *
 * <p>拦截器抛出的 {@link BusinessException} 会交由全局异常处理器统一转换成
 * {@code ApiResponse}，不需要在这里手写 JSON。
 */
public class JwtInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    private final TokenBlacklist tokenBlacklist;

    public JwtInterceptor(JwtUtil jwtUtil, TokenBlacklist tokenBlacklist) {
        this.jwtUtil = jwtUtil;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 静态资源、错误页等不是 HandlerMethod，直接放行。
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        boolean requireAuth = resolveAnnotation(handlerMethod, RequireAuth.class);
        boolean optionalAuth = resolveAnnotation(handlerMethod, OptionalAuth.class);

        if (!requireAuth && !optionalAuth) {
            return true;
        }

        String token = extractBearerToken(request);
        if (token == null) {
            if (requireAuth) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "authorization header is required");
            }
            return true;
        }

        JwtPayload payload;
        try {
            payload = jwtUtil.parseAccessToken(token);
            tokenBlacklist.assertNotRevoked(payload);
        } catch (BusinessException ex) {
            if (requireAuth) {
                throw ex;
            }
            // 可选登录场景下 token 非法不影响匿名访问。
            return true;
        }

        UserContext.set(payload.getUserId(), payload.getRole());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 线程池会复用线程，必须清理，否则下一个请求可能读到上一个用户的身份。
        UserContext.clear();
    }

    /**
     * 解析方法级或类级注解，方法级优先。
     *
     * @param handlerMethod 当前处理器方法
     * @param annotationType 注解类型
     * @return 是否标注了该注解
     */
    private boolean resolveAnnotation(HandlerMethod handlerMethod, Class<? extends java.lang.annotation.Annotation> annotationType) {
        if (handlerMethod.getMethodAnnotation(annotationType) != null) {
            return true;
        }
        return handlerMethod.getBeanType().getAnnotation(annotationType) != null;
    }

    /**
     * 从 {@code Authorization} 头提取 Bearer 令牌。
     *
     * @return 令牌字符串；缺失或格式不符时返回 {@code null}
     */
    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || header.isBlank()) {
            return null;
        }

        String trimmed = header.trim();
        if (trimmed.length() <= BEARER_PREFIX.length()
                || !trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }

        String token = trimmed.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

}
