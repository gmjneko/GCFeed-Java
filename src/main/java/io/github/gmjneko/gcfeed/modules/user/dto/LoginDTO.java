package io.github.gmjneko.gcfeed.modules.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 接收账号密码凭证，不允许客户端指定身份和角色。 */
public record LoginDTO(@NotBlank String account, @NotBlank String password) {

}
