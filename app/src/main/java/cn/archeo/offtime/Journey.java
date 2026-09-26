package cn.archeo.offtime;

import android.database.sqlite.SQLiteDatabase;
import java.util.List;

final class Journey {
    static final long STEP = 600_000L;
    static final int[] STOPS = {24, 72, 144, 240};
    static final String[] NAMES = {"苔石入口", "风铃林", "溪边木桥", "暮光观景台"};
    static final String[] NOTES = {
            "路从一块长着青苔的石头开始。慢一点，也是在向前。",
            "风穿过树梢，留下了一阵轻响。这里的时间有自己的步调。",
            "桥很短，水却流了很远。停一停，看看它要去哪里。",
            "抵达这里时，天色刚好温柔。旅程可以暂时停在这一页。"
    };

    // Called inside Collector's SQLite transaction. The cursor makes this operation idempotent.
    static void settle(Store store, SQLiteDatabase db, long baseline, long end) {
        long cursor = store.get("journey_cursor", -1);
        if (cursor < 0) cursor = store.get("journey_origin", store.get("first", baseline));
        if (cursor >= end) { store.put(db, "journey_cursor", end); return; }
        long total = store.get("journey_total", 0);
        List<Store.Span> confirmed = Intervals.confirmed(store.spans(cursor, end),
                store.coverage(cursor, end), cursor, end);
        for (Store.Span span : confirmed) {
            long before = total;
            total += span.end - span.start;
            for (int i = 0; i < STOPS.length; i++) {
                long milestone = STOPS[i] * STEP;
                if (before < milestone && total >= milestone && store.get("journey_stop_" + i, -1) < 0)
                    store.put(db, "journey_stop_" + i, span.start + milestone - before);
            }
        }
        store.put(db, "journey_total", total);
        store.put(db, "journey_cursor", end);
    }

    static long total(Store store) { return store.get("journey_total", 0); }
    static int steps(long total) { return (int) Math.min(240, total / STEP); }
    static int next(int steps) {
        for (int i = 0; i < STOPS.length; i++) if (steps < STOPS[i]) return i;
        return -1;
    }
}
