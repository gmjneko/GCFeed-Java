package io.github.gmjneko.gcfeed.modules.feed.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Feed 游标页；非空末页也返回位置，客户端以 has_more 判断是否继续。 */
public record FeedPageVO(
        String scene,
        List<FeedItemVO> items,
        @JsonProperty("next_cursor") String nextCursor,
        @JsonProperty("has_more") boolean hasMore
) {
}
