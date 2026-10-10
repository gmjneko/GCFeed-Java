package io.github.gmjneko.gcfeed.config;

import io.github.gmjneko.gcfeed.common.jwt.JwtProperties;
import io.github.gmjneko.gcfeed.common.jwt.JwtUtil;
import io.github.gmjneko.gcfeed.common.jwt.TokenBlacklist;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** 装配 JWT、令牌黑名单和密码编码器，供业务服务与 Web 鉴权配置复用。 */
@Configuration(proxyBeanMethods = false)
public class AuthenticationConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public JwtUtil jwtUtil(JwtProperties properties) {
        return new JwtUtil(properties);
    }

    @Bean
    public TokenBlacklist tokenBlacklist(RedissonClient redissonClient) {
        return new TokenBlacklist(redissonClient);
    }

}
