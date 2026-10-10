package io.github.gmjneko.gcfeed.common.jwt;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 JWT 配置绑定与令牌签发校验链路。
 */
@SpringBootTest
class JwtUtilTest {

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void propertiesBound() {
        assertThat(jwtProperties.getSecret()).isNotBlank();
        assertThat(jwtProperties.getAccessTtlSeconds()).isEqualTo(900L);
        assertThat(jwtProperties.getIssuer()).isEqualTo("gcfeed");
    }

    @Test
    void signAndParse() {
        String token = jwtUtil.signAccessToken(42L, "user");
        assertThat(token).isNotBlank();

        JwtPayload payload = jwtUtil.parseAccessToken(token);
        assertThat(payload.getUserId()).isEqualTo(42L);
        assertThat(payload.getRole()).isEqualTo("user");
        assertThat(payload.getTokenType()).isEqualTo(JwtUtil.TOKEN_TYPE_ACCESS);
        assertThat(payload.getJwtId()).isNotBlank();
        assertThat(payload.remainingSeconds()).isPositive();
    }

    @Test
    void rejectsTokensWithoutRevocationMetadata() {
        assertUnauthorized(sign(claims().id(null)));
        assertUnauthorized(sign(claims().expiration(null)));
    }

    @Test
    void rejectsWrongIssuerTypeExpiredOrTamperedTokens() {
        assertUnauthorized(sign(claims().issuer("another-service")));
        assertUnauthorized(sign(claims().claim("token_type", "refresh")));
        assertUnauthorized(sign(claims().expiration(new Date(System.currentTimeMillis() - 60_000))));
        assertUnauthorized(sign(claims().claim("uid", 0)));
        assertUnauthorized(sign(claims().claim("token_type", 123)));
        String token = jwtUtil.signAccessToken(42L, "user");
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        assertUnauthorized(String.join(".", parts));
    }

    @Test
    void rejectsSignedTokensUsingAnotherAlgorithm() {
        String token = claims().signWith(Keys.hmacShaKeyFor(jwtProperties.getSecret()
                .getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS384).compact();
        assertUnauthorized(token);
    }

    private JwtBuilder claims() {
        return Jwts.builder().issuer(jwtProperties.getIssuer()).id("test-jti")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .claim("uid", 42).claim("role", "user").claim("token_type", "access");
    }

    private String sign(JwtBuilder builder) {
        return builder.signWith(Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8)),
                Jwts.SIG.HS256).compact();
    }

    private void assertUnauthorized(String token) {
        assertThatThrownBy(() -> jwtUtil.parseAccessToken(token)).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
    }

}
