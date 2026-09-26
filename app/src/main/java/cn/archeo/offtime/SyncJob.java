package cn.archeo.offtime;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class SyncJob extends JobService {
    private static final int JOB_ID = 19371;
    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(context, SyncJob.class))
                .setPeriodic(15 * 60 * 1000L).setPersisted(true).build();
        scheduler.schedule(job);
    }
    @Override public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try { new Collector(getApplicationContext()).sync(); TodayWidget.refresh(getApplicationContext()); }
            catch (RuntimeException error) { android.util.Log.w("Offtime", "Background sync failed", error); }
            finally { jobFinished(params, false); }
        }, "offtime-sync").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
