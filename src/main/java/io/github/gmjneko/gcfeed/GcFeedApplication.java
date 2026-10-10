package io.github.gmjneko.gcfeed;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * GCFeed 应用启动类。
 *
 * <p>包扫描范围覆盖 {@code io.github.gmjneko.gcfeed} 下的全部子包，
 * 包含 {@code common}（统一响应、异常、JWT、拦截器）与 {@code modules}（各业务模块）。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GcFeedApplication {

    public static void main(String[] args) {
        SpringApplication.run(GcFeedApplication.class, args);
    }

}
