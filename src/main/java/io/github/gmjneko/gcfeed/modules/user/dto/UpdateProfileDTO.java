package io.github.gmjneko.gcfeed.modules.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 接收可编辑资料；字段缺失或 null 表示保留原值，头像和简介允许用空串清空。 */
public record UpdateProfileDTO(String nickname, @JsonProperty("avatar_url") String avatarUrl, String bio) {

}
