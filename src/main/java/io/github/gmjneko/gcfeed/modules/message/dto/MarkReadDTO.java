package io.github.gmjneko.gcfeed.modules.message.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** {@code messageIds} 缺省或空列表表示把当前用户的全部未读标为已读。 */
public record MarkReadDTO(@JsonProperty("message_ids") List<Long> messageIds) {
}
