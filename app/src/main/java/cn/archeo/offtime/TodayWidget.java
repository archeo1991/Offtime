package cn.archeo.offtime;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.time.Instant;
import java.time.ZoneId;

public final class TodayWidget extends AppWidgetProvider {
    static void refresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, TodayWidget.class);
        int[] ids = manager.getAppWidgetIds(component);
        if (ids == null || ids.length == 0) return;

        boolean permitted = new Collector(context).permitted();
        long now = System.currentTimeMillis();
        String value, status;
        if (!permitted) {
            value = "等待授权";
            status = "点击打开息间，开启使用情况访问权限";
        } else {
            Store store = new Store(context);
            Stats.Day day;
            try {
                day = Stats.day(store, Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate(), now);
            } finally { store.close(); }
            if (!day.hasData) {
                value = "等待记录";
                status = "首次锁屏并唤醒后开始显示";
            } else {
                long minutes = day.duration / 60000;
                value = minutes == 0 ? "不足1分钟" : minutes >= 60
                        ? minutes / 60 + "小时 " + minutes % 60 + "分钟" : minutes + "分钟";
                status = day.covered < now - day.start - 60000
                        ? "数据不完整 · 仅计入已确认时段" : "屏幕非交互时间 · 点击查看详情";
            }
        }
        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(context, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today);
            views.setTextViewText(R.id.widget_duration, value);
            views.setTextViewText(R.id.widget_status, status);
            views.setOnClickPendingIntent(R.id.widget_root, pending);
            manager.updateAppWidget(id, views);
        }
    }

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        // A launcher update may happen long after the last background sync.
        final PendingResult result = goAsync();
        new Thread(() -> {
            try {
                new Collector(context).sync();
                refresh(context);
            } catch (RuntimeException error) {
                android.util.Log.w("Offtime", "Widget refresh failed", error);
            } finally { result.finish(); }
        }, "offtime-widget").start();
        SyncJob.schedule(context);
    }

    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action) || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) refresh(context);
    }
}
