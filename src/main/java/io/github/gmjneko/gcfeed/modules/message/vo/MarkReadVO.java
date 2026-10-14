package io.github.gmjneko.gcfeed.modules.message.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MarkReadVO(@JsonProperty("updated_count") int updatedCount) {
}
