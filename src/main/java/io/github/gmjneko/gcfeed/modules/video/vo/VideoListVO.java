package io.github.gmjneko.gcfeed.modules.video.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 使用 published_at + id 游标分页的作品列表响应。 */
public record VideoListVO(
        List<VideoVO> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {
}
