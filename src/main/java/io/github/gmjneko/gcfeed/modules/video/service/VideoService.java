package io.github.gmjneko.gcfeed.modules.video.service;

import io.github.gmjneko.gcfeed.modules.video.dto.CreateVideoDTO;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoListVO;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoVO;

/** 视频发布、查询、作品列表和删除能力。 */
public interface VideoService {

    CreateResult createPublished(Long authorId, CreateVideoDTO dto, String idempotencyKey);

    VideoVO get(Long videoId);

    /** 内部写业务使用，调用方必须已开启事务；返回作者 ID，用于评论删除鉴权。 */
    Long lockForInteraction(Long videoId, boolean requirePublished);

    VideoListVO listPublicByAuthor(Long authorId, int limit, String cursor);

    VideoListVO listMine(Long authorId, int limit, String cursor);

    void delete(Long authorId, Long videoId);

    /** 发布结果用于区分首次创建和业务键命中的已有视频。 */
    record CreateResult(VideoVO video, boolean created) {}
}
