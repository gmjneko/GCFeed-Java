package io.github.gmjneko.gcfeed.modules.embedding.service.impl;

import io.github.gmjneko.gcfeed.modules.embedding.repository.VideoEmbeddingRepository;
import io.github.gmjneko.gcfeed.modules.embedding.service.EmbeddingService;
import io.github.gmjneko.gcfeed.modules.embedding.service.Vectorizer;
import io.github.gmjneko.gcfeed.modules.embedding.service.VideoPublishedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 向量生成的业务规则。
 *
 * <p>不声明事务：写路径是一个 upsert，本身就是原子操作；套一层事务只会让调用方
 * （发布后事件监听器）的事务语义更绕。批量补偿在 Repository 层逐条 upsert，
 * 单条失败不影响同批其他视频，下次扫描会重试失败的那条。
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    private final Vectorizer vectorizer;
    private final VideoEmbeddingRepository repository;

    public EmbeddingServiceImpl(Vectorizer vectorizer, VideoEmbeddingRepository repository) {
        this.vectorizer = vectorizer;
        this.repository = repository;
    }

    @Override
    public boolean generateForPublishedVideo(VideoPublishedEvent event) {
        if (event == null || !event.usable()) {
            return false;
        }
        String text = buildVideoText(event.title(), event.description());
        return save(event.videoId(), text);
    }

    @Override
    public int backfillMissing(int limit) {
        if (limit <= 0) {
            return 0;
        }
        List<Long> videoIds = repository.findVideoIdsMissingEmbedding(vectorizer.model(), limit);
        if (videoIds.isEmpty()) {
            return 0;
        }
        Map<Long, VideoEmbeddingRepository.VideoText> texts = repository.findTexts(videoIds);
        int generated = 0;
        for (Long videoId : videoIds) {
            VideoEmbeddingRepository.VideoText video = texts.get(videoId);
            if (video == null) {
                // 视频在查询与读取之间被删除，跳过即可，下一轮不会再选中它。
                continue;
            }
            try {
                if (save(videoId, buildVideoText(video.title(), video.description()))) {
                    generated++;
                }
            } catch (RuntimeException ex) {
                // 单条失败不中断整批：下一轮补偿会重新选中它，不让一条坏数据卡住队列。
                log.warn("向量补偿生成失败 videoId={}", videoId, ex);
            }
        }
        return generated;
    }

    private boolean save(Long videoId, String text) {
        double[] vector = vectorizer.vectorize(text);
        repository.upsert(videoId, vectorizer.model(), vector, textHash(text));
        return true;
    }

    /**
     * 拼出向量输入：标题和简介用换行连接。
     *
     * <p>标题是检索价值最高的字段，放在前面只是为了让落库的文本便于人工排查；
     * 哈希 n-gram 本身不区分位置，词袋特征会一视同仁地统计。
     */
    private String buildVideoText(String title, String description) {
        String normalizedTitle = normalize(title);
        String normalizedDescription = normalize(description);
        if (normalizedDescription.isEmpty()) {
            return normalizedTitle;
        }
        if (normalizedTitle.isEmpty()) {
            return normalizedDescription;
        }
        return normalizedTitle + "\n" + normalizedDescription;
    }

    /**
     * 文本摘要，用于判断内容是否变化。
     *
     * <p>与 Go 版一致：对去首尾空白后的文本取 SHA-256，输出 64 位十六进制。
     * 刻意不按行裁剪再拼，保证同一段文本在任何一端算出的哈希相同。
     */
    private String textHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalize(text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 是 JDK 必须提供的算法，走到这里说明运行环境异常。
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
