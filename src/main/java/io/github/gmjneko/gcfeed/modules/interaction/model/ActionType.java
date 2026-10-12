package io.github.gmjneko.gcfeed.modules.interaction.model;

import io.github.gmjneko.gcfeed.modules.video.model.VideoStatField;

/** 行为与计数字段仅由服务端枚举选择，不能拼接客户端提供的列名。 */
public enum ActionType {
    LIKE(VideoStatField.LIKE_COUNT),
    FAVORITE(VideoStatField.FAVORITE_COUNT);

    private final VideoStatField statField;

    ActionType(VideoStatField statField) {
        this.statField = statField;
    }

    public VideoStatField statField() {
        return statField;
    }
}
