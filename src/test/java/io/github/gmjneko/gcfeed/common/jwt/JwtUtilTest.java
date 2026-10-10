package io.github.gmjneko.gcfeed.common.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

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

}
