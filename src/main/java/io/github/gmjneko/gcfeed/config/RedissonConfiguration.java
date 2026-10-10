package io.github.gmjneko.gcfeed.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/** 复用 spring.data.redis 连接配置，为令牌吊销提供共享存储。 */
@Configuration(proxyBeanMethods = false)
public class RedissonConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password,
            @Value("${spring.data.redis.database:0}") int database) {
        Config config = new Config();
        // 无 Redis 时仍可启动和注册；需要校验黑名单的请求必须实际连接成功。
        config.setLazyInitialization(true);
        config.useSingleServer()
                .setAddress("redis://%s:%d".formatted(host, port))
                .setDatabase(database)
                .setConnectTimeout(2000)
                .setTimeout(1000)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(16);
        if (StringUtils.hasText(password)) {
            config.setPassword(password);
        }
        return Redisson.create(config);
    }

}
