package io.github.gmjneko.gcfeed.common.pagination;

import java.time.LocalDateTime;

/** 按时间、ID 双键排序的分页位置，保留数据库时间精度。 */
public record TimeIdCursor(LocalDateTime time, Long id) {
}
