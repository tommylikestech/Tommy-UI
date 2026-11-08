package com.tommyui.storage;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.storage.VolumeInfo;

/** Offer storage controls for newly installed apps from installers with their own sessions. */
public final class NewAppReceiver extends BroadcastReceiver {
    static final String CHANNEL = "app_locations";
    static final String ACTION_REVIEW = "com.tommyui.storage.REVIEW_APP_STORAGE";

    @Override public void onReceive(Context context, Intent event) {
        if (!Intent.ACTION_PACKAGE_ADDED.equals(event.getAction()) || event.getData() == null
                || event.getBooleanExtra(Intent.EXTRA_REPLACING, false)
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        String pkg = event.getData().getSchemeSpecificPart();
        if (pkg == null || pkg.equals(context.getPackageName())) return;
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo app = pm.getApplicationInfo(pkg, 0);
            if ((app.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) return;
            if ("com.android.packageinstaller".equals(pm.getInstallSourceInfo(pkg).getInstallingPackageName())) return;
            VolumeInfo current = pm.getPackageCurrentVolume(app);
            boolean alternative = false;
            for (VolumeInfo volume : pm.getPackageCandidateVolumes(app)) {
                boolean internal = VolumeInfo.ID_PRIVATE_INTERNAL.equals(volume.getId());
                boolean sd = volume.getDisk() != null && volume.getDisk().isSd();
                if (current != null && !current.getId().equals(volume.getId()) && volume.isMountedWritable()
                        && volume.getType() == VolumeInfo.TYPE_PRIVATE && (internal || sd)) alternative = true;
            }
            if (!alternative) return;
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "New app storage choices",
                    NotificationManager.IMPORTANCE_DEFAULT));
            Intent review = new Intent(context, MainActivity.class).setAction(ACTION_REVIEW).setData(event.getData());
            PendingIntent tap = PendingIntent.getActivity(context, 0, review, PendingIntent.FLAG_IMMUTABLE);
            String label = app.loadLabel(pm).toString();
            Notification note = new Notification.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_storage).setContentTitle("Choose storage for " + label)
                    .setContentText("Review the app’s location on your phone or SD card.")
                    .setContentIntent(tap).setAutoCancel(true).build();
            manager.notify(pkg, 1, note);
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
            // The app, card, or current user can disappear while a broadcast is in flight.
        }
    }
}
