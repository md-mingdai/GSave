package com.mingdai.savegit.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 快照时间显示：绝对时间（本地时区）与相对时间。
 */
public final class SnapshotTime {
    private static final DateTimeFormatter ABSOLUTE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60 * MINUTE_MS;
    private static final long DAY_MS = 24 * HOUR_MS;

    private SnapshotTime() {
    }

    public static String absolute(long epochMillis) {
        return ABSOLUTE.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String relative(long epochMillis) {
        long delta = System.currentTimeMillis() - epochMillis;
        if (delta < MINUTE_MS) {
            return L10n.tr("gsave.time.just_now");
        }
        if (delta < HOUR_MS) {
            return L10n.tr("gsave.time.minutes_ago", String.valueOf(delta / MINUTE_MS));
        }
        if (delta < DAY_MS) {
            return L10n.tr("gsave.time.hours_ago", String.valueOf(delta / HOUR_MS));
        }
        if (delta <= 7 * DAY_MS) {
            return L10n.tr("gsave.time.days_ago", String.valueOf(delta / DAY_MS));
        }
        return DATE.format(Instant.ofEpochMilli(epochMillis));
    }

    /** 列表/详情里的组合显示：绝对时间 · 相对时间。 */
    public static String full(long epochMillis) {
        return absolute(epochMillis) + " · " + relative(epochMillis);
    }
}
