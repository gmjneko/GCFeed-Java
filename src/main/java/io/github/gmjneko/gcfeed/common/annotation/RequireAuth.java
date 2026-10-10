package io.github.gmjneko.gcfeed.common.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记接口需要登录态。
 *
 * <p>被该注解标记的方法（或所在 Controller 类）必须携带合法 JWT，
 * 否则 {@code JwtInterceptor} 直接返回 401，不会进入业务逻辑。
 *
 * <p>之所以用注解而不是路径匹配，是因为存在
 * {@code /api/users/me}（需登录）与 {@code /api/users/{userId}}（可匿名）
 * 这类同前缀但鉴权要求不同的路由，路径通配符很难准确表达。
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuth {

}
