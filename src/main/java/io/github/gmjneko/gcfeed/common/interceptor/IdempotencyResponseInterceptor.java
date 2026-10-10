package io.github.gmjneko.gcfeed.common.interceptor;

import io.github.gmjneko.gcfeed.common.idempotency.IdempotentAspect;
import io.github.gmjneko.gcfeed.common.idempotency.IdempotentResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 将幂等业务结果映射为 HTTP 成功状态。
 *
 * <p>拦截器只处理带有 {@code @Idempotent} 的业务调用留下的请求属性，
 * 其它接口保持 Spring 默认的 {@code 200} 状态。
 */
public class IdempotencyResponseInterceptor implements HandlerInterceptor {

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView modelAndView) {
        if (!(handler instanceof HandlerMethod)) {
            return;
        }
        Object value = request.getAttribute(IdempotentAspect.RESULT_ATTRIBUTE);
        if (value instanceof IdempotentResult result && !response.isCommitted()) {
            response.setStatus(result.created()
                    ? HttpServletResponse.SC_CREATED
                    : HttpServletResponse.SC_OK);
        }
    }
}
