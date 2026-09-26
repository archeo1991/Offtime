package cn.archeo.offtime;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int BG = 0xfff7f6f2, INK = 0xff202d2b, GREEN = 0xff496f60;
    private static final int PALE = 0xffdce7db, MUTED = 0xff63716d, WARM = 0xff9c623b;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int page = 0, range = 7, recordOffset = 0;
    private long first = -1;
    private boolean allowed;
    private LinearLayout root, body;
    private ScrollView pageScroll;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (!getPreferences(MODE_PRIVATE).contains("journey_arrivals_seen")) {
            Store store = new Store(this);
            try {
                getPreferences(MODE_PRIVATE).edit().putInt("journey_arrivals_seen",
                        Journey.steps(Journey.total(store))).apply();
            } finally { store.close(); }
        }
        SyncJob.schedule(this);
    }
    @Override protected void onResume() {
        super.onResume();
        refresh();
        if (!allowed && !getPreferences(MODE_PRIVATE).getBoolean("permission_prompt_seen", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("permission_prompt_seen", true).apply();
            handler.post(() -> {
                if (!isFinishing() && !new Collector(this).permitted()) {
                    new AlertDialog.Builder(this)
                            .setTitle("开启息屏记录")
                            .setMessage("息间需要使用情况访问权限，才能读取系统的屏幕交互与非交互事件。记录仅保存在本机，不会推断睡眠或专注。")
                            .setPositiveButton("前往开启", (dialog, which) -> openPermission())
                            .setNegativeButton("暂不开启", null)
                            .show();
                }
            });
        }
    }
    private void refresh() {
        allowed = new Collector(this).permitted();
        new Thread(() -> {
            try {
                new Collector(getApplicationContext()).sync();
                TodayWidget.refresh(getApplicationContext());
                Store store = new Store(getApplicationContext());
                try { first = store.get("first", -1); } finally { store.close(); }
            } catch (RuntimeException error) {
                android.util.Log.w("Offtime", "Foreground sync failed", error);
            }
            handler.post(() -> { if (!isFinishing()) render(); });
        }, "offtime-refresh").start();
        render();
    }
    private int dp(float n) { return (int) (getResources().getDisplayMetrics().density * n + 0.5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius)); return bg;
    }
    private TextView text(String value, int sp, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(null, Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void label(LinearLayout parent, String value, int size, int color, boolean bold) {
        parent.addView(text(value, size, color, bold));
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout l = column(); l.setPadding(dp(20), dp(20), dp(20), dp(20)); l.setBackground(shape(0xffffffff, 22));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(14); parent.addView(l, p); return l;
    }
    private TextView action(String value, Runnable click) {
        TextView v = text(value, 15, 0xffffffff, true);
        v.setGravity(Gravity.CENTER); v.setBackground(shape(GREEN, 14));
        v.setMinHeight(dp(50)); v.setOnClickListener(w -> click.run()); return v;
    }
    private void render() {
        root = column(); root.setBackgroundColor(BG);
        root.setPadding(dp(16), dp(12), dp(16), 0);
        setContentView(root);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(page == 3 ? "设置" : page == 4 ? "林间小径" : "息间", 28, INK, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(56), 1));
        TextView settings = text(page == 3 || page == 4 ? "返回" : "设置", 15, GREEN, true);
        settings.setGravity(Gravity.CENTER); settings.setMinWidth(dp(56));
        settings.setOnClickListener(v -> { page = page == 3 || page == 4 ? 0 : 3; render(); });
        header.addView(settings); root.addView(header);
        pageScroll = new ScrollView(this); pageScroll.setFillViewport(true); pageScroll.setVerticalScrollBarEnabled(false);
        body = column(); body.setPadding(0, dp(12), 0, dp(28)); pageScroll.addView(body);
        root.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (page == 0) today(); else if (page == 1) trends(); else if (page == 2) records(); else if (page == 4) journey(); else settings();
        if (page != 3 && page != 4) navigation();
    }
    private void navigation() {
        LinearLayout nav = new LinearLayout(this); nav.setPadding(0, dp(4), 0, dp(4));
        String[] items = {"今日", "趋势", "记录"};
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            TextView item = text(items[i], 16, page == i ? GREEN : MUTED, page == i);
            item.setGravity(Gravity.CENTER); item.setMinHeight(dp(56));
            if (page == i) item.setBackground(shape(PALE, 16));
            item.setOnClickListener(v -> { page = index; render(); });
            nav.addView(item, new LinearLayout.LayoutParams(0, dp(56), 1));
        }
        root.addView(nav);
    }
    private void permission(LinearLayout into) {
        LinearLayout c = card(into);
        label(c, "放下手机的时间，也值得被看见", 21, INK, true); space(c, 10);
        label(c, "息间记录系统的屏幕非交互时间，不推断专注或睡眠。数据仅保存在本机。", 15, MUTED, false);
        space(c, 18); c.addView(action("开启使用情况访问权限", this::openPermission));
    }
    private void openPermission() {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }
    private Stats.Day day(LocalDate date, long now) {
        Store store = new Store(this);
        try { return Stats.day(store, date, now); } finally { store.close(); }
    }
    private void today() {
        long now = System.currentTimeMillis();
        LocalDate date = LocalDate.now();
        label(body, DATE.format(date) + "  ·  让注意力回到生活", 14, MUTED, false); space(body, 22);
        if (!allowed) { permission(body); journeyCard(); return; }
        Stats.Day d = day(date, now);
        if (!d.hasData) {
            LinearLayout c = card(body);
            label(c, "正在等待第一段息屏记录", 20, INK, true); space(c, 8);
            label(c, "锁屏后再唤醒手机，记录便会逐渐出现。", 15, MUTED, false);
            journeyCard();
            return;
        }
        LinearLayout c = card(body);
        label(c, "今日息屏", 17, MUTED, false); space(c, 8);
        label(c, duration(d.duration), 38, INK, true); space(c, 4);
        label(c, "屏幕非交互时间 · 已确认部分", 13, MUTED, false);
        if (d.covered < now - d.start - 60000) { space(c, 10); label(c, "数据不完整 · 未知时段未计入", 14, WARM, true); }
        journeyCard();
        space(body, 10); label(body, "今天的时间轴", 19, INK, true); space(body, 10);
        LinearLayout chart = card(body);
        chart.addView(new Timeline(d, now), new LinearLayout.LayoutParams(-1, dp(68)));
        LinearLayout hours = new LinearLayout(this);
        String[] ticks = {"00", "06", "12", "18", "24"};
        for (int i = 0; i < ticks.length; i++) {
            TextView tick = text(ticks[i], 11, MUTED, false);
            tick.setGravity(i == 0 ? Gravity.LEFT : i == 4 ? Gravity.RIGHT : Gravity.CENTER);
            hours.addView(tick, new LinearLayout.LayoutParams(0, dp(22), 1));
        }
        chart.addView(hours);
        space(chart, 8); label(chart, "绿色：息屏  ·  灰色：已知时段  ·  空白：未知", 12, MUTED, false);
        chart.setOnClickListener(v -> { page = 2; recordOffset = 0; render(); });
        Store.Span longest = null; long longestTime = 0;
        for (Store.Span s : d.spans) {
            long time = Stats.overlap(s.start, s.end, d.start, Math.min(now, d.end));
            if (time > longestTime) { longestTime = time; longest = s; }
        }
        c = card(body); label(c, "最长连续息屏", 16, MUTED, false); space(c, 7);
        label(c, longest == null ? "暂无已确认区间" : duration(longestTime), 23, INK, true);
        if (longest != null) { space(c, 5); label(c, time(longest.start) + " — " + time(longest.end), 14, MUTED, false); }
        space(body, 8); label(body, "每一段放下屏幕的时间，都有自己的意义。", 14, MUTED, false);
    }
    private void journeyCard() {
        Store store = new Store(this);
        long total;
        try { total = Journey.total(store); } finally { store.close(); }
        int steps = Journey.steps(total), next = Journey.next(steps);
        LinearLayout card = card(body);
        label(card, "正在漫游  ·  林间小径                         ↗", 16, GREEN, true);
        space(card, 10);
        if (!allowed) label(card, "旅程暂停更新 · 可回看地图", 19, INK, true);
        else if (next == -1) label(card, "已抵达全部四处地标", 20, INK, true);
        else if (total == 0) label(card, "从第一段息屏开始漫游", 19, INK, true);
        else label(card, "前往 " + Journey.NAMES[next], 20, INK, true);
        space(card, 8);
        label(card, steps + " / 240 格" + (next == -1 ? "" : "  ·  距下一处 " + (Journey.STOPS[next] - steps) + " 格"), 14, MUTED, false);
        space(card, 8);
        label(card, "根据已确认的屏幕非交互时间生成", 12, MUTED, false);
        card.setOnClickListener(v -> openJourney());
    }

    private void openJourney() {
        page = 4;
        render();
        handler.post(this::showArrivals);
    }

    private void showArrivals() {
        if (page != 4 || isFinishing()) return;
        Store store = new Store(this);
        int steps;
        try { steps = Journey.steps(Journey.total(store)); } finally { store.close(); }
        int seen = getPreferences(MODE_PRIVATE).getInt("journey_arrivals_seen", 0);
        if (seen > steps) {
            getPreferences(MODE_PRIVATE).edit().putInt("journey_arrivals_seen", 0).apply();
            seen = 0;
        }
        int index = Journey.next(seen);
        if (index < 0 || steps < Journey.STOPS[index]) return;
        getPreferences(MODE_PRIVATE).edit().putInt("journey_arrivals_seen", Journey.STOPS[index]).apply();
        new AlertDialog.Builder(this).setTitle("抵达 · " + Journey.NAMES[index])
                .setMessage(Journey.NOTES[index])
                .setPositiveButton("查看明信片", (dialog, which) -> {
                    Store saved = new Store(this);
                    long reached;
                    try { reached = saved.get("journey_stop_" + index, -1); } finally { saved.close(); }
                    postcard(index, reached);
                })
                .setNeutralButton("跳过提示", (dialog, which) -> {
                    getPreferences(MODE_PRIVATE).edit().putInt("journey_arrivals_seen", steps).apply();
                })
                .setNegativeButton("继续", (dialog, which) -> handler.post(this::showArrivals)).show();
    }

    private void journey() {
        Store store = new Store(this);
        long total;
        long[] reached = new long[4];
        try {
            total = Journey.total(store);
            for (int i = 0; i < 4; i++) reached[i] = store.get("journey_stop_" + i, -1);
        } finally { store.close(); }
        int steps = Journey.steps(total), next = Journey.next(steps);
        int unlocked = 0;
        for (int stop : Journey.STOPS) if (steps >= stop) unlocked++;
        label(body, "让时间带你走一段路", 15, MUTED, false); space(body, 8);
        label(body, "已抵达 " + unlocked + " / 4 个地标  ·  累计 " + steps + " 格", 17, INK, true);
        space(body, 18);
        LinearLayout scene = card(body);
        scene.setBackground(shape(PALE, 24));
        scene.addView(new Landscape(steps), new LinearLayout.LayoutParams(-1, dp(140)));
        if (!allowed) {
            label(body, "当前暂停更新 · 授权后继续漫游", 14, WARM, true);
            space(body, 10);
        } else if (total == 0) {
            label(body, "从第一段息屏开始漫游", 15, MUTED, false); space(body, 10);
        }
        if (next == -1) label(body, "林间小径 · 已走过。随时可以回看。", 15, GREEN, true);
        else {
            long remain = Journey.STOPS[next] * Journey.STEP - total;
            label(body, "下一站 " + Journey.NAMES[next] + " · 约需累计 " + duration(Math.max(0, remain)), 15, INK, true);
        }
        space(body, 18);
        label(body, "林间地图 · 向上漫游", 19, INK, true); space(body, 12);
        JourneyArt.MapView map = new JourneyArt.MapView(this, steps, index -> {
            if (steps >= Journey.STOPS[index]) postcard(index, reached[index]);
            else new AlertDialog.Builder(this).setTitle(Journey.NAMES[index])
                    .setMessage("还差 " + (Journey.STOPS[index] - steps) + " 格抵达这里。")
                    .setPositiveButton("继续漫游", null).show();
        });
        body.addView(map, new LinearLayout.LayoutParams(-1, dp(1050)));
        // Start near the traveller; users can scroll upwards to preview the destination.
        pageScroll.post(() -> {
            if (page == 4 && map.getParent() != null) {
                int focusY = Math.round(map.getTop() + dp(1050) * JourneyArt.y(steps / 240f));
                pageScroll.scrollTo(0, Math.max(0, focusY - pageScroll.getHeight() / 2));
            }
        });
        // Explicit text controls remain available to TalkBack and large-font users.
        space(body, 18); label(body, "地标速览", 18, INK, true); space(body, 10);
        for (int i = 3; i >= 0; i--) {
            final int index = i;
            boolean open = steps >= Journey.STOPS[i];
            LinearLayout stop = card(body);
            stop.setBackground(shape(open ? 0xffffffff : 0xffeeefeb, 20));
            label(stop, (open ? "●  " : "○  ") + Journey.NAMES[i], 19, INK, true);
            space(stop, 6);
            label(stop, Journey.STOPS[i] + " 格  ·  " + (open ? "查看明信片 ›" : "尚未抵达 · 还差 " + (Journey.STOPS[i] - steps) + " 格"), 14, open ? GREEN : MUTED, open);
            stop.setContentDescription(Journey.NAMES[i] + (open ? "，已解锁，查看明信片" : "，未解锁，还差 " + (Journey.STOPS[i] - steps) + " 格"));
            stop.setOnClickListener(v -> {
                if (open) postcard(index, reached[index]);
                else new AlertDialog.Builder(this).setTitle(Journey.NAMES[index])
                        .setMessage("还差 " + (Journey.STOPS[index] - steps) + " 格抵达这里。")
                        .setPositiveButton("继续漫游", null).show();
            });
        }
        TextView explanation = text("旅程说明  ·  每 10 分钟已确认息屏时间前进 1 格", 14, GREEN, false);
        explanation.setMinHeight(dp(54));
        explanation.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("关于漫游")
                .setMessage("每累计 10 分钟已确认的屏幕非交互时间，旅人前进 1 格。不足 10 分钟的时间会保留。未知时段不计入，漫游也不代表真实步数、睡眠或专注。")
                .setPositiveButton("知道了", null).show());
        body.addView(explanation);
    }

    private void postcard(int index, long reached) {
        LinearLayout content = column();
        content.setPadding(dp(18), dp(12), dp(18), dp(8));
        content.addView(new JourneyArt.PostcardView(this, index), new LinearLayout.LayoutParams(-1, dp(215)));
        space(content, 16);
        label(content, Journey.NOTES[index], 17, INK, false);
        space(content, 14);
        label(content, reached < 0 ? "已抵达" : "抵达于 " + fullTime(reached), 13, MUTED, false);
        new AlertDialog.Builder(this).setTitle("✦  " + Journey.NAMES[index])
                .setView(content).setPositiveButton("返回地图", (dialog, which) -> handler.post(this::showArrivals)).show();
    }
    private final class Landscape extends View {
        private final Paint paint = new Paint(3);
        private final int steps;
        Landscape(int steps) {
            super(MainActivity.this); this.steps = steps;
            setContentDescription("林间小径插画，旅程已走过 " + steps + " 格");
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            paint.setColor(0xfff7f6f2);
            canvas.drawCircle(w * .78f, h * .28f, dp(26), paint);
            paint.setColor(0xffa8bdae);
            for (int i = 0; i < 5; i++) {
                float x = w * (.12f + i * .2f), y = h * (.56f + (i % 2) * .08f);
                canvas.drawCircle(x, y, dp(20 + (i % 2) * 8), paint);
                canvas.drawRect(x - dp(2), y, x + dp(2), h * .76f, paint);
            }
            paint.setColor(0xffeef1e8);
            canvas.drawOval(new RectF(w * .15f, h * .67f, w * .85f, h * 1.4f), paint);
            paint.setColor(GREEN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(5));
            paint.setStrokeCap(Paint.Cap.ROUND);
            android.graphics.Path path = new android.graphics.Path();
            path.moveTo(w * .5f, h * .95f);
            path.cubicTo(w * .21f, h * .8f, w * .8f, h * .66f, w * .52f, h * .47f);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0xffd79a70);
            float x = w * (.5f + .13f * (float) Math.sin(steps / 240f * Math.PI));
            canvas.drawCircle(x, h * .74f, dp(8), paint);
        }
    }
    private void trends() {
        label(body, "过去的日子", 23, INK, true); space(body, 6);
        label(body, "按当前时区统计 · 不完整日期不参与平均", 13, MUTED, false); space(body, 18);
        LinearLayout switcher = new LinearLayout(this);
        for (int n : new int[]{7, 30}) {
            TextView tab = text("最近 " + n + " 天", 15, range == n ? GREEN : MUTED, range == n);
            tab.setGravity(Gravity.CENTER); tab.setMinHeight(dp(48));
            tab.setOnClickListener(v -> { range = n; render(); });
            switcher.addView(tab, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        body.addView(switcher); space(body, 12);
        if (!allowed) { permission(body); return; }
        long now = System.currentTimeMillis();
        Store store = new Store(this);
        List<Stats.Day> days;
        try { days = Stats.days(store, range, now); } finally { store.close(); }
        long total = 0; int count = 0;
        for (Stats.Day d : days) if (d.complete) { total += d.duration; count++; }
        LinearLayout c = card(body); label(c, "日均息屏", 16, MUTED, false); space(c, 6);
        label(c, count == 0 ? "暂无完整日期" : duration(total / count), count == 0 ? 23 : 34, INK, true);
        space(c, 5); label(c, count == 0 ? "需要至少一个完整且可信的自然日" : "基于 " + count + " 天完整数据", 13, MUTED, false);
        Collections.reverse(days);
        c = card(body); label(c, "每日息屏", 18, INK, true); space(c, 18);
        if (range == 7) {
            c.addView(new Bars(days), new LinearLayout.LayoutParams(-1, dp(180)));
        } else {
            HorizontalScrollView scroller = new HorizontalScrollView(this);
            scroller.setHorizontalScrollBarEnabled(false);
            scroller.addView(new Bars(days), new ViewGroup.LayoutParams(dp(870), dp(180)));
            c.addView(scroller);
        }
        space(c, 12); label(c, "浅色柱：数据不完整 · 点击柱形查看当日", 12, MUTED, false);
        for (int i = days.size() - 1; i >= 0; i--) {
            Stats.Day d = days.get(i);
            TextView line = text(d.date.getMonthValue() + "/" + d.date.getDayOfMonth() + "     " + (d.hasData ? duration(d.duration) : "暂无数据") + (d.complete ? "" : "  ·  不完整"), 14, INK, false);
            line.setMinHeight(dp(48));
            line.setOnClickListener(v -> showDay(d)); body.addView(line);
        }
    }
    private void showDay(Stats.Day d) {
        new AlertDialog.Builder(this).setTitle(DATE.format(d.date))
                .setMessage((d.hasData ? "息屏 " + duration(d.duration) + "\n息屏段 " + d.spans.size() + " 段" : "暂无数据")
                        + (d.complete ? "" : "\n数据不完整，仅计入已确认时段"))
                .setPositiveButton("知道了", null).show();
    }
    private void records() {
        LocalDate selected = LocalDate.now().minusDays(recordOffset);
        label(body, "息屏记录", 23, INK, true); space(body, 16);
        LinearLayout dates = new LinearLayout(this); dates.setGravity(Gravity.CENTER_VERTICAL);
        TextView older = text("‹  前一天", 15, GREEN, true); older.setMinHeight(dp(48));
        older.setOnClickListener(v -> { recordOffset++; render(); }); dates.addView(older, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView date = text(DATE.format(selected), 15, INK, true); date.setGravity(Gravity.CENTER);
        dates.addView(date, new LinearLayout.LayoutParams(0, dp(48), 2));
        TextView newer = text("后一天  ›", 15, recordOffset == 0 ? MUTED : GREEN, true); newer.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        newer.setMinHeight(dp(48)); newer.setOnClickListener(v -> { if (recordOffset > 0) { recordOffset--; render(); } });
        dates.addView(newer, new LinearLayout.LayoutParams(0, dp(48), 1)); body.addView(dates); space(body, 14);
        if (!allowed) { permission(body); return; }
        Stats.Day d = day(selected, System.currentTimeMillis());
        if (!d.complete) { label(body, "数据不完整 · 仅展示已确认的区间", 14, WARM, true); space(body, 14); }
        if (d.spans.isEmpty()) {
            LinearLayout c = card(body); label(c, "还没有可展示的息屏记录", 18, INK, true);
            space(c, 8); label(c, "未知时段不会被当作零时长。", 14, MUTED, false); return;
        }
        List<Store.Span> spans = new ArrayList<>(d.spans); Collections.reverse(spans);
        for (Store.Span s : spans) {
            long part = Stats.overlap(s.start, s.end, d.start, d.end);
            if (part <= 0) continue;
            LinearLayout c = card(body);
            long displayedEnd = Math.min(s.end, d.end);
            label(c, time(Math.max(s.start, d.start)) + "  —  " + (displayedEnd == d.end ? "24:00" : time(displayedEnd)), 18, INK, true);
            space(c, 6); label(c, duration(part) + "  ·  已确认", 14, GREEN, true);
            c.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("息屏段详情")
                    .setMessage("本日已确认起点：" + fullTime(s.start) + "\n本日已确认终点：" + fullTime(s.end)
                            + "\n本日计入：" + duration(part) + "\n\n屏幕非交互不等于睡眠或专注。")
                    .setPositiveButton("关闭", null).show());
        }
    }
    private void settings() {
        label(body, "偏好与隐私", 23, INK, true); space(body, 18);
        LinearLayout c = card(body); label(c, "使用情况访问权限", 18, INK, true); space(c, 6);
        label(c, allowed ? "已开启" : "未开启 · 开启后开始记录", 15, allowed ? GREEN : WARM, true);
        space(c, 15); c.addView(action("打开系统权限设置", this::openPermission));
        c = card(body); label(c, "统计口径", 18, INK, true); space(c, 8);
        label(c, "从系统的屏幕非交互事件到下一次交互事件；仅计算可确认的时段。跨天按当前时区分配，未确认的时段标为数据不完整。息屏不代表睡眠或专注。", 15, MUTED, false);
        c = card(body); label(c, "本地数据", 18, INK, true); space(c, 8);
        label(c, "记录与漫游进度仅保存在本机，不需要账户或联网。首次记录：" + (first < 0 ? "尚无" : fullTime(first)), 15, MUTED, false);
        space(c, 12);
        TextView clear = text("清除本地记录", 15, WARM, true); clear.setMinHeight(dp(48));
        clear.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("清除本地记录？")
                .setMessage("此操作会删除应用保存的息屏记录、漫游进度和已解锁明信片，无法撤销。系统的使用记录不会被删除。")
                .setNegativeButton("取消", null).setPositiveButton("清除", (dialog, which) -> {
                    new Thread(() -> {
                        synchronized (Collector.class) {
                            Store store = new Store(getApplicationContext()); store.clear(); store.close();
                        }
                        TodayWidget.refresh(getApplicationContext());
                        handler.post(() -> {
                            getPreferences(MODE_PRIVATE).edit().putInt("journey_arrivals_seen", 0).apply();
                            first = -1; render();
                        });
                    }, "offtime-clear").start();
                }).show());
        c.addView(clear);
        label(body, "Offtime · cn.archeo.offtime", 12, MUTED, false);
    }
    private static String duration(long ms) {
        if (ms < 60000) return "不足1分钟";
        long minutes = ms / 60000;
        return minutes >= 60 ? (minutes / 60) + "小时 " + (minutes % 60) + "分钟" : minutes + "分钟";
    }
    private static String time(long ms) { return CLOCK.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())); }
    private static String fullTime(long ms) { return DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA).format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())); }

    private final class Timeline extends View {
        final Stats.Day day; final long now; final Paint paint = new Paint(3);
        final List<Store.Span> covered;
        Timeline(Stats.Day day, long now) { super(MainActivity.this); this.day = day; this.now = now;
            Store store = new Store(MainActivity.this);
            try { covered = store.coverage(day.start, Math.min(day.end, now)); } finally { store.close(); }
            setContentDescription("今日息屏 " + duration(day.duration) + (day.covered < Math.min(now, day.end) - day.start ? "，数据不完整" : "")); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); float width = getWidth(), top = dp(12), height = dp(38);
            paint.setColor(BG); canvas.drawRoundRect(new RectF(0, top, width, top + height), dp(9), dp(9), paint);
            drawSegments(canvas, day.start, day.end, covered, 0xffe0e3df, top, height);
            drawSegments(canvas, day.start, day.end, day.spans, GREEN, top, height);
            paint.setColor(WARM);
            float marker = width * (Math.min(now, day.end) - day.start) / (float) (day.end - day.start);
            canvas.drawRect(marker, top - dp(5), marker + dp(2), top + height + dp(5), paint);
        }
        private void drawSegments(Canvas c, long from, long to, List<Store.Span> spans, int color, float top, float h) {
            paint.setColor(color);
            for (Store.Span s : spans) {
                float l = getWidth() * Math.max(0, s.start - from) / (float) (to - from);
                float r = getWidth() * (Math.min(Math.min(s.end, now), to) - from) / (float) (to - from);
                if (r > l) c.drawRect(l, top, r, top + h, paint);
            }
        }
    }
    private final class Bars extends View {
        final List<Stats.Day> days; final Paint paint = new Paint(3);
        Bars(List<Stats.Day> days) { super(MainActivity.this); this.days = days; setContentDescription("每日息屏柱形图，下方提供逐日文字数据"); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float slot = getWidth() / (float) days.size(), base = getHeight() - dp(30);
            long max = 12 * 60 * 60 * 1000L;
            for (Stats.Day d : days) max = Math.max(max, d.duration);
            for (int i = 0; i < days.size(); i++) {
                Stats.Day d = days.get(i);
                float x = slot * i + slot / 2, h = (base - dp(14)) * d.duration / max;
                paint.setColor(d.complete ? GREEN : PALE);
                if (d.hasData) canvas.drawRoundRect(new RectF(x - slot * .27f, base - Math.max(dp(2), h), x + slot * .27f, base), dp(4), dp(4), paint);
                paint.setColor(MUTED); paint.setTextSize(dp(10)); paint.setTextAlign(Paint.Align.CENTER);
                if (days.size() <= 7 || i % 3 == 0) canvas.drawText(String.valueOf(d.date.getDayOfMonth()), x, getHeight() - dp(6), paint);
            }
        }
        @Override public boolean performClick() {
            super.performClick(); return true;
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                int index = Math.min(days.size() - 1, Math.max(0, (int) (event.getX() / getWidth() * days.size())));
                performClick(); showDay(days.get(index)); return true;
            }
            return true;
        }
    }
}
