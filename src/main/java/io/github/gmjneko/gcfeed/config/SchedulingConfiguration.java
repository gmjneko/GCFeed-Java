package io.github.gmjneko.gcfeed.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 启用定时任务；测试通过 gcfeed.scheduling.enabled=false 关闭，由用例手动触发任务方法。 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "gcfeed.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfiguration {

}
