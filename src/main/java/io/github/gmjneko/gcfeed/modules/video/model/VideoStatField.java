package io.github.gmjneko.gcfeed.modules.video.model;

/** video_stat 中通过增量缓冲异步累加的计数列；列名只来自枚举，不拼接外部输入。 */
public enum VideoStatField {
    LIKE_COUNT("like_count"),
    FAVORITE_COUNT("favorite_count");

    private final String column;

    VideoStatField(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }

    /** 按列名解析，未知列返回 null，供读取缓冲时忽略已废弃的字段。 */
    public static VideoStatField fromColumn(String column) {
        for (VideoStatField field : values()) {
            if (field.column.equals(column)) {
                return field;
            }
        }
        return null;
    }
}
