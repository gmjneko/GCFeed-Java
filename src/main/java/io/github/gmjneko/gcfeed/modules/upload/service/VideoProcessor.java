package io.github.gmjneko.gcfeed.modules.upload.service;

import java.nio.file.Path;

/** 视频元数据校验和容器 faststart 处理扩展点。 */
public interface VideoProcessor {

    // TODO: 现在是同步处理，后续可以异步化，上传成功后进入任务队列，由独立 worker 执行视频校验和处理
    void validate(Path path);

    void faststart(Path path);
}
