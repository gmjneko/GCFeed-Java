package io.github.gmjneko.gcfeed.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口可选登录。
 *
 * <p>接口匿名可访问；如果请求带了合法 JWT，则把用户身份写入 {@code UserContext}，
 * 业务层可据此做个性化（例如 Feed 返回当前用户的 {@code liked} / {@code favorited}）。
 *
 * <p>与 {@link RequireAuth} 的区别：token 缺失或非法都不会中断请求。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionalAuth {

}
