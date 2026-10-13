package io.github.gmjneko.gcfeed.config;

import io.github.gmjneko.gcfeed.modules.recommendation.model.RecommendationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** 装配推荐参数与可替换时钟，业务时间遵循进程时区。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RecommendationProperties.class)
public class RecommendationConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock recommendationClock() {
        return Clock.systemDefaultZone();
    }

}
