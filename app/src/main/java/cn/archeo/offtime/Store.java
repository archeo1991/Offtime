package cn.archeo.offtime;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

final class Store extends SQLiteOpenHelper {
    static final class Span {
        final long start, end;
        Span(long start, long end) { this.start = start; this.end = end; }
    }

    Store(Context context) { super(context, "offtime.db", null, 1); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE spans (id INTEGER PRIMARY KEY, start_ms INTEGER NOT NULL, end_ms INTEGER)");
        db.execSQL("CREATE TABLE coverage (start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE meta (key TEXT PRIMARY KEY, value INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX spans_time ON spans(start_ms,end_ms)");
        db.execSQL("CREATE INDEX coverage_time ON coverage(start_ms,end_ms)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }

    long get(String key, long fallback) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM meta WHERE key=?", new String[]{key})) {
            return c.moveToFirst() ? c.getLong(0) : fallback;
        }
    }
    void put(SQLiteDatabase db, String key, long value) {
        db.execSQL("INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)", new Object[]{key, value});
    }
    void clear() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("spans", null, null);
            db.delete("coverage", null, null);
            db.delete("meta", null, null);
            put(db, "journey_origin", System.currentTimeMillis());
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    List<Span> spans(long from, long to) { return query("spans", from, to, true); }
    List<Span> coverage(long from, long to) { return query("coverage", from, to, false); }
    private List<Span> query(String table, long from, long to, boolean open) {
        List<Span> result = new ArrayList<>();
        String sql = "SELECT start_ms,end_ms FROM " + table + " WHERE start_ms<? AND (end_ms>?" + (open ? " OR end_ms IS NULL" : "") + ") ORDER BY start_ms";
        try (Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(to), String.valueOf(from)})) {
            while (c.moveToNext()) result.add(new Span(c.getLong(0), c.isNull(1) ? Long.MAX_VALUE : c.getLong(1)));
        }
        return result;
    }
}
