package io.github.gmjneko.gcfeed.common.pagination;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record CursorPage<T>(
        List<T> items,
        @JsonProperty("next_cursor")
        String nextCursor,
        @JsonProperty("has_more")
        boolean hasMore
) {
}
