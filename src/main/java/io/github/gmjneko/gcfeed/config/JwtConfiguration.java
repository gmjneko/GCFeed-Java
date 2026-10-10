package io.github.gmjneko.gcfeed.config;

import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.common.jwt.TokenBlacklist;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 认证相关的 Bean 装配。
 *
 * <p>单独成一个配置类而不是塞进 {@code WebMvcConfiguration}：
 * 后者需要注入 {@link JwtUtil} 来构建拦截器，如果 Bean 也定义在同一个类里，
 * 就会形成「类定义 Bean、类又依赖该 Bean」的自引用循环依赖。
 */
@Configuration
public class JwtConfiguration {

    /**
     * JWT 工具 Bean。
     *
     * <p>构造函数会校验密钥长度，密钥不足 32 字节时直接抛异常导致启动失败，
     * 避免误用弱密钥上线。
     */
    @Bean
    public JwtUtil jwtUtil(io.github.gmjneko.gcfeed.common.jwt.JwtProperties jwtProperties) {
        return new JwtUtil(jwtProperties);
    }

    /**
     * 令牌黑名单 Bean。
     *
     * <p>通过 {@link ObjectProvider} 注入 Redis 客户端：未配置 Redis 时
     * {@code getIfAvailable()} 返回 {@code null}，黑名单自动降级为不校验，
     * 应用仍可正常启动。
     */
    @Bean
    public TokenBlacklist tokenBlacklist(ObjectProvider<RedissonClient> redissonClientProvider) {
        return new TokenBlacklist(redissonClientProvider);
    }

}
