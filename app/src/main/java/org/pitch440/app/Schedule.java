package org.pitch440.app;

import java.time.*;
import java.util.Set;

/** Random intervals consume eligible time only. Selected weekdays own the window's start. */
public final class Schedule {
    private Schedule() {}
    private static ZonedDateTime[] window(LocalDate day, ZoneId zone, int start, int end) {
        ZonedDateTime a = day.atTime(LocalTime.ofSecondOfDay(start * 60L)).atZone(zone);
        ZonedDateTime b = (end <= start ? day.plusDays(1) : day)
            .atTime(LocalTime.ofSecondOfDay(end * 60L)).atZone(zone);
        return new ZonedDateTime[]{a, b};
    }
    public static boolean active(long now, ZoneId zone, int start, int end, Set<Integer> days) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (int offset = -1; offset <= 0; offset++) {
            LocalDate day = today.plusDays(offset);
            ZonedDateTime[] w = window(day, zone, start, end);
            if (days.contains(day.getDayOfWeek().getValue()) && now >= w[0].toInstant().toEpochMilli()
                && now < w[1].toInstant().toEpochMilli()) return true;
        }
        return false;
    }
    public static long next(long now, ZoneId zone, int start, int end, Set<Integer> days,
                            int count, boolean perDay, double random, long expires) {
        if (days.isEmpty() || count < 1 || random < 0 || random >= 1) return -1;
        int minutes = (end - start + 1440) % 1440;
        if (minutes == 0) minutes = 1440;
        double mean = (perDay ? minutes * 60000.0 : 3600000.0) / count;
        long remaining = Math.max(300000L, (long)(mean * (0.5 + random)));
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        for (int offset = -1; offset < 370; offset++) {
            LocalDate day = today.plusDays(offset);
            if (!days.contains(day.getDayOfWeek().getValue())) continue;
            ZonedDateTime[] w = window(day, zone, start, end);
            long a = Math.max(now, w[0].toInstant().toEpochMilli());
            long b = w[1].toInstant().toEpochMilli();
            if (b <= a) continue;
            if (remaining < b - a) {
                long result = a + remaining;
                return expires > 0 && result >= expires ? -1 : result;
            }
            remaining -= b - a;
        }
        return -1;
    }
}
