package io.github.gmjneko.gcfeed.modules.embedding.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 零依赖的本地伪 embedding：字符 n-gram + 词元特征，哈希到固定维度并做 L2 归一化。
 *
 * <p>这不是语义向量，只是让推荐链路先跑通的占位实现：文本越相似，命中的
 * n-gram 越多，余弦相似度越高。好处是没有任何外部依赖、完全确定（同一文本
 * 永远得到同一向量），不需要调用模型服务，离线也能测。
 *
 * <p>算法与 Go 版 {@code hash-ngram-v1} 逐位对齐（FNV-1a 64 位哈希 → 取模选维
 * → 用最高位定符号），因此两端并行运行时 {@code video_embedding} 表可以共用，
 * 同一段文本算出的向量完全一致。
 *
 * <p>符号哈希（signed hashing）是这里唯一需要留意的技巧：同一个 n-gram 落在
 * 哪一维由哈希决定，正负号也由哈希决定。这样两个不同特征撞到同一维时可以
 * 相互抵消，而不是无脑累加——后者会让所有向量趋同、区分度下降。
 */
public final class HashNgramVectorizer implements Vectorizer {

    /** 模型标识，与 Go 版共用同一张表和同一份向量，不能改名。 */
    public static final String MODEL = "hash-ngram-v1";

    /** 默认向量维度。128 维足以让短文本的 n-gram 特征分散开，又不会撑大 JSON。 */
    public static final int DEFAULT_DIMENSION = 128;

    /** FNV-1a 64 位偏移基数，取值来自算法规范。 */
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;

    /** FNV-1a 64 位质数。 */
    private static final long FNV_PRIME = 0x100000001b3L;

    /** n-gram 的最小与最大长度。2-gram 抓词形，3-gram 兼顾短语搭配。 */
    private static final int MIN_GRAM = 2;
    private static final int MAX_GRAM = 3;

    private final int dimension;

    public HashNgramVectorizer() {
        this(DEFAULT_DIMENSION);
    }

    public HashNgramVectorizer(int dimension) {
        this.dimension = dimension > 0 ? dimension : DEFAULT_DIMENSION;
    }

    @Override
    public String model() {
        return MODEL;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    /**
     * 生成向量：词元特征 + 字符 n-gram 特征，最后 L2 归一化。
     *
     * <p>归一化是必须的：不同长度的标题和简介会产生数量差异巨大的特征，
     * 不归一化就变成「文本越长向量模长越大」，余弦相似度也就失去意义。
     */
    @Override
    public double[] vectorize(String text) {
        double[] vector = new double[dimension];
        String normalized = normalizeText(text);
        if (normalized.isEmpty()) {
            return vector;
        }

        // 词元（空格分词）承载整体词形信息，字符 n-gram 承载子串重叠信息（对中文尤其重要）。
        for (String token : normalized.split(" ")) {
            if (!token.isEmpty()) {
                addFeature(vector, "tok:" + token);
            }
        }

        // 去掉空格后的连续字符序列上滑窗，避免词边界把跨词搭配切碎。
        int[] codePoints = normalized.replace(" ", "").codePoints().toArray();
        for (int n = MIN_GRAM; n <= MAX_GRAM; n++) {
            if (codePoints.length < n) {
                continue;
            }
            for (int i = 0; i + n <= codePoints.length; i++) {
                addFeature(vector, new String(codePoints, i, n));
            }
        }

        normalizeVector(vector);
        return vector;
    }

    /**
     * 计算两个向量的余弦相似度，取值范围 {@code [-1, 1]}。
     *
     * <p>维度不一致返回 0 而不是抛异常：向量来自数据库，模型换版本时新旧向量
     * 共存是正常现象，此时按「无法比较」处理比让整个推荐请求失败更合适。
     */
    public static double cosineSimilarity(double[] left, double[] right) {
        if (left == null || right == null || left.length != right.length) {
            return 0;
        }
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < left.length; i++) {
            dot += left[i] * right[i];
            leftNorm += left[i] * left[i];
            rightNorm += right[i] * right[i];
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /**
     * 文本归一化：转小写，只保留字母和数字，其余字符（空白、标点、符号）压成单个空格。
     *
     * <p>标点压成空格而不是直接丢弃，是为了让 {@code "hello,world"} 和 {@code "hello world"}
     * 得到相同的词元切分。连续多个标点只产生一个空格，避免切出空词元。
     *
     * <p>按 code point 而非 char 遍历，避免代理对（emoji、扩展汉字）被截断成半个字符。
     */
    static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        String lower = text.strip().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(lower.length());
        boolean previousSpace = true;
        for (int i = 0; i < lower.length(); ) {
            int codePoint = lower.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint)) {
                builder.appendCodePoint(codePoint);
                previousSpace = false;
                continue;
            }
            // 空白、标点、符号统一视为分隔符；其他类别（如控制字符）直接丢弃。
            if (!previousSpace) {
                builder.append(' ');
                previousSpace = true;
            }
        }
        return builder.toString().strip();
    }

    /**
     * 把一个特征累加进向量：哈希选维，最高位定正负。
     *
     * <p>{@code >> 63} 取出符号位，{@code & 1} 得到 0 或 1，与 Go 版
     * {@code (hash >> 63) & 1} 完全一致。用 {@code & Long.MAX_VALUE} 取模而不是
     * {@code Math.abs(hash % dimension)}，是为了让 {@code Long.MIN_VALUE} 也能得到非负结果。
     */
    private void addFeature(double[] vector, String feature) {
        if (feature.isEmpty()) {
            return;
        }
        vector[featureSlot(feature, dimension)] += featureSign(feature);
    }

    /**
     * 特征落位，包级可见以便测试锁定跨语言一致性。
     *
     * <p>{@code hash & Long.MAX_VALUE} 把有符号数当作无符号数取模，与 Go 版
     * {@code int(hash % uint64(len(vector)))} 等价；直接用 {@code Math.abs} 会在
     * {@code Long.MIN_VALUE} 上溢出。
     */
    static int featureSlot(String feature, int dimension) {
        return (int) ((fnv1a64(feature) & Long.MAX_VALUE) % dimension);
    }

    /** 特征方向：哈希最高位为 1 时取负，与 Go 版 {@code (hash >> 63) & 1} 一致。 */
    static double featureSign(String feature) {
        return ((fnv1a64(feature) >> 63) & 1) == 1 ? -1.0 : 1.0;
    }

    /** FNV-1a 64 位哈希，逐字节处理；UTF-8 编码与 Go 版 {@code []byte(feature)} 一致。 */
    private static long fnv1a64(String feature) {
        long hash = FNV_OFFSET_BASIS;
        for (byte value : feature.getBytes(StandardCharsets.UTF_8)) {
            hash ^= (value & 0xff);
            hash *= FNV_PRIME;
        }
        return hash;
    }

    /** 就地 L2 归一化；零向量保持不变。 */
    private static void normalizeVector(double[] vector) {
        double sum = 0;
        for (double value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = vector[i] / norm;
        }
    }
}
