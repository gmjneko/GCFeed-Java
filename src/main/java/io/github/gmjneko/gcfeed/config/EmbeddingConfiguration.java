package io.github.gmjneko.gcfeed.config;

import io.github.gmjneko.gcfeed.modules.embedding.service.HashNgramVectorizer;
import io.github.gmjneko.gcfeed.modules.embedding.service.Vectorizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 向量能力装配。
 *
 * <p>单独成配置类而不是给实现打 {@code @Component}：换成真实 embedding 模型时
 * 只需在这里改一个 {@code @Bean}，不用动实现类，也不会和 {@code @ConditionalOnMissingBean}
 * 的语义打架。
 */
@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {

    /**
     * 默认使用零依赖的本地哈希 n-gram 向量器。
     *
     * <p>维度可配（{@code gcfeed.embedding.dimension}）：改维度等于换向量空间，必须同时
     * 换模型标识，否则新旧向量会被 {@code uk_video_model} 判为同一份互相覆盖。因此维度
     * 只在启动时读取一次，改配置后需要重新生成全部向量。
     */
    @Bean
    @ConditionalOnMissingBean(Vectorizer.class)
    public Vectorizer vectorizer(@Value("${gcfeed.embedding.dimension:128}") int dimension) {
        return new HashNgramVectorizer(dimension);
    }
}
