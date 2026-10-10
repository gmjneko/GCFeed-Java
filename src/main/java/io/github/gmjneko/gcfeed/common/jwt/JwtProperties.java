package io.github.gmjneko.gcfeed.common.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置项，对应 {@code application.yaml} 中的 {@code gcfeed.jwt.*}。
 */
@ConfigurationProperties(prefix = "gcfeed.jwt")
public class JwtProperties {

    /** 签名密钥。HS256 要求密钥不少于 256 bit，配置值过短会导致启动失败。 */
    private String secret = "gcfeed-default-dev-secret-please-change-in-production";

    /** 访问令牌有效期（秒），默认 15 分钟，与 Go 版 {@code access_ttl: 15m} 保持一致。 */
    private long accessTtlSeconds = 900L;

    /** 签发者标识，写入 JWT 的 {@code iss} 声明。 */
    private String issuer = "gcfeed";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getAccessTtlSeconds() {
        return accessTtlSeconds;
    }

    public void setAccessTtlSeconds(long accessTtlSeconds) {
        this.accessTtlSeconds = accessTtlSeconds;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

}
