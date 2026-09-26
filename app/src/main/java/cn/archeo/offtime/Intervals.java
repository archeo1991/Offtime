package cn.archeo.offtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Intersect confirmed screen-off events with observed coverage; never infer missing time. */
final class Intervals {
    private Intervals() { }

    static List<Store.Span> confirmed(List<Store.Span> spans, List<Store.Span> coverage, long from, long to) {
        List<Store.Span> observed = union(coverage, from, to);
        List<Store.Span> result = new ArrayList<>();
        for (Store.Span span : spans) {
            for (Store.Span window : observed) {
                long start = Math.max(Math.max(span.start, window.start), from);
                long end = Math.min(Math.min(span.end, window.end), to);
                if (end > start) result.add(new Store.Span(start, end));
            }
        }
        return union(result, from, to);
    }

    static List<Store.Span> union(List<Store.Span> source, long from, long to) {
        List<Store.Span> sorted = new ArrayList<>(source);
        sorted.sort(Comparator.comparingLong(s -> s.start));
        List<Store.Span> result = new ArrayList<>();
        for (Store.Span part : sorted) {
            long start = Math.max(part.start, from), end = Math.min(part.end, to);
            if (end <= start) continue;
            if (!result.isEmpty() && start <= result.get(result.size() - 1).end) {
                Store.Span last = result.remove(result.size() - 1);
                result.add(new Store.Span(last.start, Math.max(last.end, end)));
            } else result.add(new Store.Span(start, end));
        }
        return result;
    }

    static long length(List<Store.Span> spans) {
        long sum = 0;
        for (Store.Span span : spans) sum += span.end - span.start;
        return sum;
    }
}
