package cn.archeo.offtime;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

final class Stats {
    static final class Day {
        final LocalDate date;
        final long start, end, duration, covered;
        final List<Store.Span> spans;
        final boolean complete, hasData;
        Day(LocalDate date, long start, long end, long duration, long covered,
            List<Store.Span> spans, boolean complete, boolean hasData) {
            this.date = date; this.start = start; this.end = end; this.duration = duration;
            this.covered = covered; this.spans = spans; this.complete = complete; this.hasData = hasData;
        }
    }

    static Day day(Store store, LocalDate date, long now) {
        ZoneId zone = ZoneId.systemDefault();
        long from = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        long visibleEnd = Math.min(to, now);
        List<Store.Span> coverage = Intervals.union(store.coverage(from, visibleEnd), from, visibleEnd);
        List<Store.Span> spans = Intervals.confirmed(store.spans(from, visibleEnd), coverage, from, visibleEnd);
        long duration = Intervals.length(spans), covered = Intervals.length(coverage);
        boolean hasData = covered > 0;
        boolean complete = to <= now && covered >= to - from;
        return new Day(date, from, to, duration, covered, spans, complete, hasData);
    }

    static long overlap(long a, long b, long from, long to) {
        return Math.max(0, Math.min(b, to) - Math.max(a, from));
    }
    static List<Day> days(Store store, int count, long now) {
        List<Day> result = new ArrayList<>();
        LocalDate today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
        for (int i = 0; i < count; i++) result.add(day(store, today.minusDays(i), now));
        return result;
    }
}
