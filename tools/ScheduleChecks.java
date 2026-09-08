import org.pitch440.app.Schedule;
import java.time.*;
import java.util.*;

public class ScheduleChecks {
    static final ZoneId Z = ZoneId.of("Europe/Vienna");
    static long at(String value) { return LocalDateTime.parse(value).atZone(Z).toInstant().toEpochMilli(); }
    static void check(boolean value, String name) { if (!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        Set<Integer> weekdays = Set.of(1,2,3,4,5);
        check(Schedule.active(at("2026-09-08T09:00"), Z, 540, 1200, weekdays), "start inclusive");
        check(!Schedule.active(at("2026-09-08T20:00"), Z, 540, 1200, weekdays), "end exclusive");
        check(!Schedule.active(at("2026-09-12T12:00"), Z, 540, 1200, weekdays), "weekend excluded");
        check(Schedule.active(at("2026-09-12T01:00"), Z, 1320, 120, Set.of(5)), "overnight belongs to Friday");
        check(!Schedule.active(at("2026-09-12T22:00"), Z, 1320, 120, Set.of(5)), "Saturday not selected");
        long next = Schedule.next(at("2026-09-11T19:55"), Z, 540, 1200, weekdays, 3, false, .5, 0);
        check(next == at("2026-09-14T09:15"), "carry eligible minutes across weekend");
        check(Schedule.next(at("2026-09-08T09:00"), Z, 540, 1200, weekdays, 3, false, .5, at("2026-09-08T09:10")) == -1, "expiry");
        check(Schedule.next(at("2026-09-08T09:00"), Z, 540, 1200, Set.of(), 3, false, .5, 0) == -1, "no days");
        Random rng = new Random(440);
        for (int i = 0; i < 10000; i++) {
            long now = at("2026-01-01T00:00") + (long)(rng.nextDouble() * 365 * 86400000L);
            int start = rng.nextInt(1440), end = rng.nextInt(1440);
            long n = Schedule.next(now, Z, start, end, weekdays, 1 + rng.nextInt(6), rng.nextBoolean(), rng.nextDouble(), 0);
            check(n >= now + 300000, "minimum spacing");
            check(Schedule.active(n, Z, start, end, weekdays), "scheduled inside eligible window including DST");
        }
        for (String date : List.of("2026-03-29T01:50", "2026-10-25T01:50")) {
            long n = Schedule.next(at(date), Z, 0, 0, Set.of(7), 3, false, .5, 0);
            check(n == at(date) + 1200000, "elapsed time across DST");
        }
        System.out.println("Passed boundary, overnight, weekend, expiry, DST and 10,000 randomized scheduling checks.");
    }
}
