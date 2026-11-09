package com.android.settings.tommyui;

import android.app.job.*;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.os.SystemProperties;
import com.android.settings.R;
import java.io.InputStream;

public final class UpdateCheck extends JobService {
    static UpdateFeed fetch(Context context) throws Exception {
        try (InputStream in = context.getResources().openRawResource(R.raw.tommy_feed_key)) {
            return UpdateFeed.fetch(in.readAllBytes());
        }
    }
    static boolean eligible(UpdateFeed r) {
        return !r.url.isEmpty() && Build.VERSION.INCREMENTAL.equals(r.sourceBuild)
                && "XT2413-2".equals(SystemProperties.get("ro.boot.hardware.sku"))
                && "fogona".equals(SystemProperties.get("ro.product.vendor.device"))
                && r.vendor.equals(SystemProperties.get("ro.vendor.build.fingerprint"))
                && (SystemProperties.getBoolean("ro.build.ab_update", false)
                    || SystemProperties.getBoolean("ro.virtual_ab.enabled", false));
    }
    public static final class Boot extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent intent) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction()))
                c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(0x544d5901,
                    new ComponentName(c,UpdateCheck.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setBackoffCriteria(60_000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
        }
    }
    private volatile int generation;
    @Override public boolean onStartJob(JobParameters params) {
        int run = ++generation;
        new Thread(() -> {
            boolean retry = false;
            try {
                UpdateFeed feed = fetch(this);
                if (run == generation && UpdateFeed.ordinal(feed.version) >
                        UpdateFeed.ordinal(UpdateFeed.installed(Build.VERSION.INCREMENTAL)) && eligible(feed)) {
                    NotificationManager nm = getSystemService(NotificationManager.class);
                    nm.createNotificationChannel(new NotificationChannel("tommy_updates","Tommy UI updates",
                            NotificationManager.IMPORTANCE_DEFAULT));
                    PendingIntent page = PendingIntent.getActivity(this,0,new Intent(this,UpdatePage.class),
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    nm.notify(0x544d59,new Notification.Builder(this,"tommy_updates")
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle("Tommy UI " + feed.version + " is available")
                            .setContentText(feed.summary).setContentIntent(page).setAutoCancel(true).build());
                }
            } catch (java.io.IOException e) { retry = true; }
              catch (Exception e) { android.util.Log.w("TommyUpdates","Boot check failed",e); }
            if (run == generation) jobFinished(params,retry);
        },"TommyBootCheck").start();
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { generation++; return true; }
}
