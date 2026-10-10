package io.github.gmjneko.gcfeed.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要把首次执行和幂等重放映射为不同 HTTP 成功状态的业务方法。
 *
 * <p>注解本身不负责判重。业务方法仍应使用唯一索引、状态机或其它业务约束
 * 保证幂等，并返回 {@link io.github.gmjneko.gcfeed.common.idempotency.IdempotentResult}。
 * AOP 只把结果写入当前请求，MVC 拦截器再设置首次创建 {@code 201} 或重放 {@code 200}。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Idempotent {
}
