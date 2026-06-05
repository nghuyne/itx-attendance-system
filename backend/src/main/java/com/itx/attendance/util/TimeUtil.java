package com.itx.attendance.util;

import java.time.LocalDateTime;
import java.time.ZoneId;

public final class TimeUtil {

    public static final ZoneId UTC_PLUS_7 = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final ZoneId UTC = ZoneId.of("UTC");

    private TimeUtil() {}

    public static LocalDateTime nowUtcPlus7() {
        return LocalDateTime.now(UTC_PLUS_7);
    }

    public static LocalDateTime toUtcPlus7(LocalDateTime utc) {
        return utc.atZone(UTC).withZoneSameInstant(UTC_PLUS_7).toLocalDateTime();
    }

    public static LocalDateTime toUtc(LocalDateTime utcPlus7) {
        return utcPlus7.atZone(UTC_PLUS_7).withZoneSameInstant(UTC).toLocalDateTime();
    }
}
