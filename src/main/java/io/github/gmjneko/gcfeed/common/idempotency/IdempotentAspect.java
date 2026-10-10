package io.github.gmjneko.gcfeed.common.idempotency;

import io.github.gmjneko.gcfeed.common.annotation.Idempotent;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 把幂等方法的首次/重放结果传给 MVC 响应拦截器。
 *
 * <p>这里不读取或写入幂等存储，也不改变业务返回值，避免把数据库判重规则
 * 和通用基础设施耦合。没有 HTTP 请求上下文时（例如定时任务或单元测试）直接执行。
 */
@Aspect
@Component
public class IdempotentAspect {

    public static final String RESULT_ATTRIBUTE = "gcfeed.idempotent.result";

    @Around("@annotation(idempotent)")
    public Object captureResult(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        Object result = joinPoint.proceed();
        if (result instanceof IdempotentResult idempotentResult) {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servletAttributes) {
                HttpServletRequest request = servletAttributes.getRequest();
                request.setAttribute(RESULT_ATTRIBUTE, idempotentResult);
            }
        }
        return result;
    }
}
