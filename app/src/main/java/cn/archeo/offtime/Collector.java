package cn.archeo.offtime;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Process;
import android.os.SystemClock;

final class Collector {
    private static final long MAX_GAP = 24L * 60 * 60 * 1000;
    private final Context context;
    Collector(Context context) { this.context = context.getApplicationContext(); }

    boolean permitted() {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    // All syncs are serialized on the same database monitor in this process.
    void sync() {
        Store store = new Store(context);
        synchronized (Collector.class) {
            if (!permitted()) {
                SQLiteDatabase db = store.getWritableDatabase();
                db.beginTransaction();
                try {
                    long last = store.get("last", -1);
                    if (last >= 0) db.execSQL("UPDATE spans SET end_ms=? WHERE end_ms IS NULL", new Object[]{last});
                    store.put(db, "state", -1);
                    store.put(db, "open", -1);
                    store.put(db, "last", System.currentTimeMillis());
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); store.close(); }
                return;
            }
            long now = System.currentTimeMillis();
            long last = store.get("last", store.get("journey_origin", now));
            // Clearing local data starts a new journey at the next trusted sync,
            // never replaying events still retained by Android.
            long boot = now - SystemClock.elapsedRealtime();
            long previousBoot = store.get("boot", boot);
            int state = (int) store.get("state", -1); // -1 unknown, 0 interactive, 1 non-interactive
            long open = store.get("open", -1);
            long trustedUntil = last;
            if (now < last || Math.abs(boot - previousBoot) > 120000) {
                // Never bridge an unsampled window or a restart with a speculative off interval.
                SQLiteDatabase db = store.getWritableDatabase();
                db.beginTransaction();
                try {
                    if (state == 1 && open >= 0) db.execSQL("UPDATE spans SET end_ms=? WHERE end_ms IS NULL", new Object[]{last});
                    Journey.settle(store, db, store.get("journey_cursor", last), last);
                    store.put(db, "state", -1);
                    store.put(db, "open", -1);
                    store.put(db, "last", now);
                    store.put(db, "boot", boot);
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                last = now;
                state = -1;
                open = -1;
            }
            if (now - last > MAX_GAP) {
                // Query at most one day of retained events, without extending an unverified open span.
                SQLiteDatabase db = store.getWritableDatabase();
                db.beginTransaction();
                try {
                    if (state == 1 && open >= 0)
                        db.execSQL("UPDATE spans SET end_ms=? WHERE end_ms IS NULL", new Object[]{last});
                    Journey.settle(store, db, store.get("journey_cursor", last), last);
                    store.put(db, "state", -1);
                    store.put(db, "open", -1);
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
                last = Math.max(now - MAX_GAP, Math.max(boot, store.get("journey_origin", 0)));
                state = -1;
                open = -1;
            }
            UsageStatsManager usage = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
            if (usage == null) { store.close(); return; }
            UsageEvents events;
            try { events = usage.queryEvents(last, now + 1); }
            catch (SecurityException | IllegalStateException error) { store.close(); return; }
            if (events == null) { store.close(); return; }
            SQLiteDatabase db = store.getWritableDatabase();
            db.beginTransaction();
            try {
                long coveredFrom = state == -1 ? -1 : last;
                long lastEvent = -1;
                UsageEvents.Event event = new UsageEvents.Event();
                while (events.hasNextEvent()) {
                    events.getNextEvent(event);
                    long t = event.getTimeStamp();
                    if (t < last || t > now) continue;
                    int type = event.getEventType();
                    if (type != UsageEvents.Event.DEVICE_SHUTDOWN && type != UsageEvents.Event.DEVICE_STARTUP
                            && type != UsageEvents.Event.SCREEN_NON_INTERACTIVE
                            && type != UsageEvents.Event.SCREEN_INTERACTIVE) continue;
                    // The inclusive boundary may replay a screen event; state changes are idempotent.
                    if (lastEvent >= 0 && t < lastEvent) continue;
                    lastEvent = t;
                    if (type == UsageEvents.Event.DEVICE_SHUTDOWN || type == UsageEvents.Event.DEVICE_STARTUP) {
                        // Shutdown timestamps do not prove that the display was off until shutdown.
                        if (state == 1 && open >= 0) close(db, Math.max(open, Math.min(t, trustedUntil)));
                        state = -1;
                        open = -1;
                        coveredFrom = -1;
                    } else if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE && state != 1) {
                        if (coveredFrom < 0) coveredFrom = t;
                        if (store.get("first", -1) < 0) store.put(db, "first", t);
                        db.execSQL("INSERT INTO spans(start_ms,end_ms) VALUES(?,NULL)", new Object[]{t});
                        state = 1;
                        open = t;
                    } else if (type == UsageEvents.Event.SCREEN_INTERACTIVE && state != 0) {
                        if (state == 1 && open >= 0) close(db, t);
                        if (coveredFrom < 0) coveredFrom = t;
                        if (store.get("first", -1) < 0) store.put(db, "first", t);
                        state = 0;
                        open = -1;
                    }
                }
                if (coveredFrom >= 0 && now > coveredFrom) addCoverage(db, coveredFrom, now);
                Journey.settle(store, db, last, now);
                store.put(db, "last", now);
                store.put(db, "boot", boot);
                store.put(db, "state", state);
                store.put(db, "open", open);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); store.close(); }
        }
    }
    private static void addCoverage(SQLiteDatabase db, long start, long end) {
        db.execSQL("INSERT INTO coverage(start_ms,end_ms) VALUES(?,?)", new Object[]{start, end});
    }
    private static void close(SQLiteDatabase db, long end) {
        db.execSQL("UPDATE spans SET end_ms=? WHERE end_ms IS NULL", new Object[]{end});
    }
}
