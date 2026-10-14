package io.github.gmjneko.gcfeed.modules.message.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UnreadStatVO(@JsonProperty("unread_count") int unreadCount) {
}
