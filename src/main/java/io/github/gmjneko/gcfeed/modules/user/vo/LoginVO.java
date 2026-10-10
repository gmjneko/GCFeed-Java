package io.github.gmjneko.gcfeed.modules.user.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 返回访问令牌、认证方案及有效秒数，字段名与原 Go 接口一致。
 */
public record LoginVO(
        @JsonProperty("access_token")
        String accessToken,
        @JsonProperty("token_type")
        String tokenType,
        @JsonProperty("expires_in_seconds")
        long expiresInSeconds
) {

    public static LoginVO from(String accessToken, String tokenType, long expiresInSeconds) {
        return new LoginVO(accessToken, tokenType, expiresInSeconds);
    }

}
