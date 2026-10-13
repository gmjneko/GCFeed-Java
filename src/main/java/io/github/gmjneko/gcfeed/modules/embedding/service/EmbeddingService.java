package io.github.gmjneko.gcfeed.modules.embedding.service;

/**
 * 视频内容向量的生成与补偿。
 *
 * <p>向量是推荐召回的输入，属于「派生数据」：视频发布后必须最终生成，但生成失败
 * 不应该让发布请求失败。因此写路径分两条：
 * <ol>
 *   <li>发布后事件触发即时生成，走正常链路；</li>
 *   <li>定时补偿扫描上线但没有向量的视频，兜住事件丢失、生成失败、历史数据。</li>
 * </ol>
 */
public interface EmbeddingService {

    /**
     * 为已发布视频生成并保存向量。
     *
     * <p>按 {@code (video_id, model)} upsert，重复调用是幂等的，覆盖同模型旧向量。
     * 文本没变时也照常重算，代价很低，换来的是一份确定的写路径。
     *
     * @return 是否实际写入（事件不可用或视频不存在时为 {@code false}）
     */
    boolean generateForPublishedVideo(VideoPublishedEvent event);

    /**
     * 补偿扫描：为上线但缺少向量的视频补生成，最多处理 {@code limit} 条。
     *
     * <p>由定时任务循环调用直到返回 0，单次只处理一批，避免长时间占用数据库。
     *
     * @return 本次实际生成的视频数量
     */
    int backfillMissing(int limit);
}
