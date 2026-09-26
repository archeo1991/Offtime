package cn.archeo.offtime;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;

public class IntervalsTest {
    private static Store.Span s(long start, long end) { return new Store.Span(start, end); }

    @Test public void openIntervalStopsAtLastCoveredMoment() {
        List<Store.Span> confirmed = Intervals.confirmed(
                Arrays.asList(s(10, Long.MAX_VALUE)), Arrays.asList(s(10, 30)), 0, 100);
        assertEquals(20, Intervals.length(confirmed));
        assertEquals(30, confirmed.get(0).end);
    }

    @Test public void disjointCoverageNeverFillsUnknownGap() {
        List<Store.Span> confirmed = Intervals.confirmed(Arrays.asList(s(10, 90)),
                Arrays.asList(s(10, 30), s(60, 90)), 0, 100);
        assertEquals(2, confirmed.size());
        assertEquals(50, Intervals.length(confirmed));
    }

    @Test public void overlappingWindowsAndSpansCountOnlyOnce() {
        List<Store.Span> confirmed = Intervals.confirmed(Arrays.asList(s(0, 30), s(20, 40)),
                Arrays.asList(s(0, 25), s(10, 50)), 0, 50);
        assertEquals(1, confirmed.size());
        assertEquals(40, Intervals.length(confirmed));
    }

    @Test public void midnightSplitPreservesElapsedTime() {
        List<Store.Span> spans = Arrays.asList(s(90, 110));
        List<Store.Span> coverage = Arrays.asList(s(90, 110));
        assertEquals(10, Intervals.length(Intervals.confirmed(spans, coverage, 0, 100)));
        assertEquals(10, Intervals.length(Intervals.confirmed(spans, coverage, 100, 200)));
    }

    @Test public void emptyCoverageNeverCountsScreenOff() {
        assertEquals(0, Intervals.length(Intervals.confirmed(
                Arrays.asList(s(0, Long.MAX_VALUE)), Arrays.asList(), 0, 100)));
    }
}
