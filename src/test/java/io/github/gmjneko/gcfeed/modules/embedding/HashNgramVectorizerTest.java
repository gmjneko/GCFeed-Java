package io.github.gmjneko.gcfeed.modules.embedding;

import io.github.gmjneko.gcfeed.modules.embedding.service.HashNgramVectorizer;
import io.github.gmjneko.gcfeed.modules.embedding.service.Vectorizer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 向量器的纯函数行为。
 *
 * <p>这里锁定的都是与 Go 版共用的不变量：维度、模型名、归一化规则、跨语言哈希一致性。
 * 这些一旦漂移，两端算出的向量就不再可比，推荐结果会静默变差。
 */
class HashNgramVectorizerTest {

    private final Vectorizer vectorizer = new HashNgramVectorizer();

    @Test
    void vectorIsDeterministicAndNormalized() {
        double[] first = vectorizer.vectorize("first video\nhello timeline");
        double[] second = vectorizer.vectorize("first video\nhello timeline");

        assertThat(first).hasSize(HashNgramVectorizer.DEFAULT_DIMENSION);
        assertThat(first).containsExactly(second);
        assertThat(norm(first)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void blankTextReturnsZeroVector() {
        assertThat(vectorizer.vectorize(null)).containsOnly(0.0);
        assertThat(vectorizer.vectorize("   ")).containsOnly(0.0);
        assertThat(vectorizer.vectorize("!!! ???")).containsOnly(0.0);
    }

    @Test
    void similarTextScoresHigherThanUnrelatedText() {
        double[] base = vectorizer.vectorize("cooking pasta at home");
        double[] similar = vectorizer.vectorize("cooking pasta at home tonight");
        double[] unrelated = vectorizer.vectorize("quantum computing basics");

        double similarScore = HashNgramVectorizer.cosineSimilarity(base, similar);
        double unrelatedScore = HashNgramVectorizer.cosineSimilarity(base, unrelated);

        assertThat(similarScore).isGreaterThan(unrelatedScore);
        assertThat(HashNgramVectorizer.cosineSimilarity(base, base)).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    /** 大小写与标点差异不应影响向量，否则同一句话换个写法就成了另一个视频。 */
    @Test
    void normalizationIgnoresCaseAndPunctuation() {
        double[] plain = vectorizer.vectorize("Hello World");
        double[] punctuated = vectorizer.vectorize("  hello,  world!  ");

        assertThat(HashNgramVectorizer.cosineSimilarity(plain, punctuated)).isCloseTo(1.0,
                org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void dimensionIsConfigurableAndMismatchedVectorsScoreZero() {
        Vectorizer small = new HashNgramVectorizer(16);
        assertThat(small.vectorize("hello")).hasSize(16);
        assertThat(HashNgramVectorizer.cosineSimilarity(
                small.vectorize("hello"), vectorizer.vectorize("hello"))).isZero();
        assertThat(HashNgramVectorizer.cosineSimilarity(null, vectorizer.vectorize("hello"))).isZero();
    }

    /**
     * 哈希落在哪一维、取什么符号必须与 Go 版逐位一致，否则共用 {@code video_embedding} 表时
     * 两端算出的向量无法比较。期望值取自 Go 版 {@code fnv.New64a()} 的实际输出，
     * 维度 128 时的落位与符号：
     * <pre>
     * tok:hello -> idx 75  符号 -1
     * he        -> idx 118 符号 +1
     * </pre>
     */
    @Test
    void hashFeaturePositionsMatchGoImplementation() {
        assertThat(featureSlot("tok:hello", 128)).isEqualTo(75);
        assertThat(featureSign("tok:hello")).isEqualTo(-1.0);
        assertThat(featureSlot("he", 128)).isEqualTo(118);
        assertThat(featureSign("he")).isEqualTo(1.0);
    }

    /**
     * 逐位比对 FNV-1a：与 Go 版 {@code fnv.New64a()} 对同一特征算出同样的落位与符号。
     *
     * <p>这组用例的价值在于，只要哈希实现被改动（比如换成 {@code Math.abs}、改用别的
     * 哈希或改用 UTF-16 编码），Java 端算出的向量就会和 Go 端分道扬镳，而推荐结果只会
     * 「悄悄变差」不报错，所以必须由测试把这条不变量钉住。
     */
    @Test
    void hashMatchesGoForKnownFeatures() {
        // Go: fnv.New64a() 对 "tok:hello" 得到 9798042355776799051，无符号取模 128 得 75。
        assertThat(featureSlot("tok:hello", 128)).isEqualTo(75);
        // Go: "he" 得 628906390544363382，取模 128 得 118。
        assertThat(featureSlot("he", 128)).isEqualTo(118);
        // 有符号取模会得到负数，只有按无符号处理才能与 Go 对齐。
        assertThat(featureSlot("tok:hello", 128)).isNotNegative();
    }

    /**
     * 通过反射调用包级可见的哈希辅助方法。
     *
     * <p>不把它们改成 {@code public}：落位和符号是内部实现细节，暴露出去会让人误以为
     * 可以依赖；测试放在同一包下又会与 {@code modules.embedding} 的分组不一致。
     */
    private static int featureSlot(String feature, int dimension) {
        return (int) invoke("featureSlot", new Class<?>[]{String.class, int.class}, feature, dimension);
    }

    private static double featureSign(String feature) {
        return (double) invoke("featureSign", new Class<?>[]{String.class}, feature);
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) {
        try {
            Method method = HashNgramVectorizer.class.getDeclaredMethod(name, types);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("failed to invoke " + name, ex);
        }
    }

    private double norm(double[] vector) {
        double sum = 0;
        for (double value : vector) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }
}
