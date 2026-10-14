package io.github.gmjneko.gcfeed.modules.feed.hot;

import io.github.gmjneko.gcfeed.common.constant.RedisKeyConstant;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** 热榜分钟桶和一小时窗口的键。时间一律用 UTC，避免服务器时区把同一分钟写成两个桶。 */
public final class HotWindows {

    public static final int MINUTES = 60;

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private HotWindows() {
    }

    public static LocalDateTime minute(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC).truncatedTo(ChronoUnit.MINUTES);
    }

    public static String minuteKey(LocalDateTime minute) {
        return RedisKeyConstant.FEED_HOT_MINUTE.formatted(minute.truncatedTo(ChronoUnit.MINUTES).format(MINUTE));
    }

    public static String windowKey(LocalDateTime windowEnd) {
        long epoch = windowEnd.truncatedTo(ChronoUnit.MINUTES).toEpochSecond(ZoneOffset.UTC);
        return RedisKeyConstant.FEED_HOT_WINDOW.formatted(epoch);
    }

    /** 含窗口结束分钟在内的最近 60 个分钟，从旧到新。 */
    public static List<LocalDateTime> minutes(LocalDateTime windowEnd) {
        LocalDateTime end = windowEnd.truncatedTo(ChronoUnit.MINUTES);
        List<LocalDateTime> minutes = new ArrayList<>(MINUTES);
        for (int age = MINUTES - 1; age >= 0; age--) {
            minutes.add(end.minusMinutes(age));
        }
        return minutes;
    }

    public static String member(long videoId) {
        return "%020d".formatted(videoId);
    }

    /** 零填充保证分数字典序与数值序一致。全 0 或非 20 位数字不是合法视频 ID。 */
    public static Long videoId(String member) {
        if (member == null || member.length() != 20) {
            return null;
        }
        for (int i = 0; i < member.length(); i++) {
            if (member.charAt(i) < '0' || member.charAt(i) > '9') {
                return null;
            }
        }
        long id = Long.parseLong(member);
        return id <= 0 ? null : id;
    }
}
