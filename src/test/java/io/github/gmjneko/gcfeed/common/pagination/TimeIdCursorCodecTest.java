package io.github.gmjneko.gcfeed.common.pagination;

import io.github.gmjneko.gcfeed.common.api.ErrorCode;
import io.github.gmjneko.gcfeed.common.exception.BusinessException;
import io.github.gmjneko.gcfeed.modules.feed.model.FeedCursor;
import io.github.gmjneko.gcfeed.modules.video.service.VideoCursorCodec;
import io.github.gmjneko.gcfeed.modules.video.vo.VideoCursor;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimeIdCursorCodecTest {

    @Test
    void roundTripRetainsFullTimePrecisionAndUsesUrlSafeEncoding() {
        var cursor = new TimeIdCursor(LocalDateTime.parse("2026-10-11T12:34:56.123456789"), Long.MAX_VALUE);
        String encoded = TimeIdCursorCodec.encode(cursor);
        assertThat(encoded).matches("[A-Za-z0-9_-]+");
        assertThat(TimeIdCursorCodec.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void existingVideoCursorWireFormatRemainsCompatibleWithBothModules() {
        // 固定旧格式样本，不通过待测编码器生成。
        String legacy = "MjAyNi0xMC0xMVQxMjozNDo1Ni4xMjM0NTY3ODl8NDI";
        var time = LocalDateTime.parse("2026-10-11T12:34:56.123456789");
        assertThat(VideoCursorCodec.decode(legacy)).isEqualTo(new VideoCursor(time, 42L));
        assertThat(VideoCursorCodec.encode(new VideoCursor(time, 42L))).isEqualTo(legacy);
        assertThat(FeedCursor.decode(legacy)).isEqualTo(new FeedCursor(time, 42L));
        assertThat(new FeedCursor(time, 42L).encode()).isEqualTo(legacy);
    }

    @Test
    void emptyCursorIsTheFirstPage() {
        for (String value : new String[]{null, "", " \t\n"}) {
            assertThat(TimeIdCursorCodec.decode(value)).isNull();
            assertThat(VideoCursorCodec.decode(value)).isNull();
            assertThat(FeedCursor.decode(value)).isNull();
        }
    }

    @Test
    void malformedCursorsAlwaysUseTheBusinessError() {
        assertInvalid("not-a-cursor!");
        for (String raw : new String[]{"2026-10-11T12:00", "bad-time|1", "2026-10-11T12:00|",
                "2026-10-11T12:00|0", "2026-10-11T12:00|-1", "2026-10-11T12:00|abc",
                "2026-10-11T12:00|9223372036854775808", "2026-10-11T12:00|1|extra"}) {
            assertInvalid(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(raw.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void assertInvalid(String encoded) {
        assertThatThrownBy(() -> TimeIdCursorCodec.decode(encoded))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR));
    }
}
