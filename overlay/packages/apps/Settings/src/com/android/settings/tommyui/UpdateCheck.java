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
        return fetch(context, false);
    }
    static UpdateFeed fetch(Context context, boolean trial) throws Exception {
        try (InputStream in = context.getResources().openRawResource(R.raw.tommy_feed_key)) {
            boolean preview = "TommyUI.v1.100.dev.20261109".equals(Build.VERSION.INCREMENTAL)
                    && android.provider.Settings.Global.getInt(context.getContentResolver(), "tommyui_classic_preview", 0) == 1;
            return UpdateFeed.fetch(in.readAllBytes(), trial, preview);
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
    static final String NOTICE = "Use Tommy UI at your own risk. Calling and text messaging may not work on this build.";
    public static final class Notice extends Activity {
        private static android.content.SharedPreferences prefs(Context c) {
            return c.getSharedPreferences("tommy_telephony_notice", MODE_PRIVATE);
        }
        static void afterBoot(Context c) {
            if (Build.VERSION.INCREMENTAL.equals(prefs(c).getString("acceptedBuild", ""))) return;
            android.telecom.TelecomManager telecom = c.getSystemService(android.telecom.TelecomManager.class);
            if (telecom != null && telecom.isInCall()) return;
            c.startActivity(new Intent(c, Notice.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        @Override public void onCreate(android.os.Bundle state) {
            super.onCreate(state);
            new AlertDialog.Builder(this).setTitle("Calls and text messages")
                .setMessage(NOTICE)
                .setPositiveButton("I understand", (dialog, which) -> {
                    prefs(this).edit().putString("acceptedBuild", Build.VERSION.INCREMENTAL).apply();
                    finish();
                }).setOnCancelListener(dialog -> finish()).show();
        }
    }
    public static final class Boot extends BroadcastReceiver {
        @Override public void onReceive(Context c, Intent intent) {
            if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                c.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(0x544d5901,
                    new ComponentName(c,UpdateCheck.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setBackoffCriteria(60_000,JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
                Notice.afterBoot(c);
            }
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
