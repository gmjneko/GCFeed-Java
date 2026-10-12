package io.github.gmjneko.gcfeed.modules.video.service;

import io.github.gmjneko.gcfeed.modules.video.model.VideoStatField;

import java.util.List;
import java.util.Map;

/**
 * 视频计数的待刷库增量缓冲。
 *
 * <p>video_stat 是事实表的派生汇总，允许最终一致：业务事务提交后把增量记入缓冲，
 * 定时任务批量刷入数据库，对账任务再按事实表纠正丢失或重复的增量。
 */
public interface VideoStatDeltaBuffer {

    /** 累加增量，并把视频标记为待刷库。 */
    void add(Long videoId, VideoStatField field, long delta);

    /** 读取尚未刷库的增量；没有增量的字段不出现在结果中。 */
    Map<VideoStatField, Long> pending(Long videoId);

    /** 取出最多 limit 个待刷库视频；取出后调用方负责 {@link #drain(Long)}。 */
    List<Long> pollDirty(int limit);

    /** 原子地取出并清空某个视频的全部增量。 */
    Map<VideoStatField, Long> drain(Long videoId);
}
