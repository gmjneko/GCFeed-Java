package io.github.gmjneko.gcfeed.modules.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 接收注册信息；账号、密码、昵称的清洗和长度规则由服务层统一执行。 */
public record RegisterDTO(
        @NotBlank String account,
        @NotBlank String password,
        @NotBlank String nickname) {

}
