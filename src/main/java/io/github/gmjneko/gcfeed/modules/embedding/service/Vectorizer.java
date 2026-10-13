package io.github.gmjneko.gcfeed.modules.embedding.service;

/**
 * 把文本转换为固定维度向量的能力抽象。
 *
 * <p>抽象出接口是为了让推荐模块只依赖「向量」这个契约，而不关心向量怎么来。
 * 当前实现是零依赖的本地哈希 n-gram，后续换成真实模型（调用外部 embedding 服务）
 * 只需新增实现类并切换注入的 Bean。
 *
 * <p>{@code model()} 会写入 {@code video_embedding.model} 列，并与 {@code uk_video_model}
 * 唯一索引一起构成「同一视频同一模型只有一份向量」的约束。换实现必须换模型标识，
 * 否则新旧向量会被当成同一份互相覆盖。
 */
public interface Vectorizer {

    /** 向量模型标识，写入 {@code video_embedding.model}，例如 {@code hash-ngram-v1}。 */
    String model();

    /** 向量维度，写入 {@code video_embedding.dimension}。 */
    int dimension();

    /** 把文本转换为长度为 {@link #dimension()} 的向量；空文本返回零向量。 */
    double[] vectorize(String text);
}
