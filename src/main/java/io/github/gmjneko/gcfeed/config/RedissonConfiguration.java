package io.github.gmjneko.gcfeed.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 客户端配置。
 *
 * <p>连接参数直接复用 {@code spring.data.redis.*}，与 Spring Data Redis 保持同源，
 * 避免在 {@code application.yaml} 里维护两套 Redis 地址。
 *
 * <p>Redis 是「可选的加速层」：本地未启动 Redis 时应用仍应能启动，
 * 因此这里不主动连接，只构建客户端对象 —— Redisson 的连接是在首次使用时才建立的。
 */
@Configuration
public class RedissonConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RedissonConfiguration.class);

    /** Redis 地址，来自 {@code spring.data.redis.host}。 */
    @Value("${spring.data.redis.host:localhost}")
    private String host;

    /** Redis 端口。 */
    @Value("${spring.data.redis.port:6379}")
    private int port;

    /** Redis 密码，空表示无密码。 */
    @Value("${spring.data.redis.password:}")
    private String password;

    /** Redis 逻辑库编号。 */
    @Value("${spring.data.redis.database:0}")
    private int database;

    /**
     * 构建 Redisson 客户端。
     *
     * <p>如果业务已经自行定义了 {@link RedissonClient}，这里不覆盖
     * （{@link ConditionalOnMissingBean}），便于后续扩展集群或哨兵模式。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setUseScriptCache(true);

        SingleServerConfig serverConfig = config.useSingleServer()
                .setAddress("redis://%s:%d".formatted(host, port))
                .setDatabase(database)
                // 与 Go 版保持一致的短超时，Redis 抖动时快速失败而不是拖慢接口。
                .setConnectTimeout(2000)
                .setTimeout(1000)
                .setConnectionMinimumIdleSize(4)
                .setConnectionPoolSize(32);

        if (StringUtils.hasText(password)) {
            serverConfig.setPassword(password);
        }

        log.info("初始化 Redisson 客户端: redis://{}:{}/{}", host, port, database);
        return Redisson.create(config);
    }

}
