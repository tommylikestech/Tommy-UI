package com.tommyui.storage;
import android.app.*;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Gravity;
import android.widget.*;

public class AboutTommy extends Activity {
    int taps; long last;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.about_tommy);
        android.view.View root = ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        String build = android.os.Build.VERSION.INCREMENTAL;
        java.util.regex.Matcher match = java.util.regex.Pattern.compile(
                "TommyUI\\.(v[0-9]+\\.[0-9]{3})\\.dev\\.[0-9]{8}").matcher(build);
        Button version = findViewById(R.id.classic_version);
        if (match.matches()) {
            String versionName = match.group(1);
            version.setText(getString(R.string.classic_version, "v1.100".equals(versionName) ? "1.1" : versionName.substring(1)));
        } else {
            version.setText(R.string.classic_version_unknown);
        }
        version.setOnClickListener(v -> {
            long now = SystemClock.elapsedRealtime();
            if (now - last > 1300) taps = 0;
            last = now;
            if (++taps == 7) { taps = 0; constellation(); }
        });
        TextView androidVersion = findViewById(R.id.classic_android);
        androidVersion.setText(getString(R.string.classic_android, android.os.Build.VERSION.RELEASE));
        ((TextView) findViewById(R.id.classic_build)).setText(build);
        Button wallpaper = findViewById(R.id.classic_wallpaper);
        WallpaperManager manager = getSystemService(WallpaperManager.class);
        int art = getResources().getIdentifier("default_wallpaper", "drawable", "android");
        if (art == 0 || manager == null || !manager.isWallpaperSupported() || !manager.isSetWallpaperAllowed()) {
            wallpaper.setVisibility(android.view.View.GONE);
        } else {
            wallpaper.setOnClickListener(v -> previewWallpaper(manager, art, wallpaper));
        }
        Button update = findViewById(R.id.classic_update);
        update.setOnClickListener(v -> {
            android.content.Intent intent = new android.content.Intent().setClassName(
                    "com.android.settings", "com.android.settings.tommyui.UpdatePage");
            try { startActivity(intent); }
            catch (android.content.ActivityNotFoundException e) {
                new AlertDialog.Builder(this).setMessage("Install the companion Tommy Settings update to check for releases.")
                    .setPositiveButton("OK",null).show();
            }
        });
    }
    private void previewWallpaper(WallpaperManager manager, int art, Button button) {
        ImageView preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setMaxHeight(getResources().getDisplayMetrics().heightPixels / 2);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.setImageResource(art);
        new AlertDialog.Builder(this).setTitle(R.string.classic_wallpaper).setView(preview)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.classic_apply, (dialog, which) -> {
                button.setEnabled(false);
                new Thread(() -> {
                    int result;
                    try {
                        manager.setResource(art, WallpaperManager.FLAG_SYSTEM);
                        result = R.string.classic_applied;
                    } catch (java.io.IOException | RuntimeException e) {
                        result = R.string.classic_wallpaper_error;
                    }
                    final int message = result;
                    runOnUiThread(() -> {
                        if (isDestroyed()) return;
                        button.setEnabled(true);
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                    });
                }, "TommyWallpaper").start();
            }).show();
    }

    private void constellation(){
        TextView sky=new TextView(this); sky.setGravity(Gravity.CENTER); sky.setTextSize(25);
        sky.setPadding(16,64,16,64); sky.setBackgroundColor(0xff102f32); sky.setTextColor(0xffcdf9da);
        String[] stars={"✦","✦    ✧","✦    ✧\n       ✦","✦    ✧\n       ✦     ✧","✦    ✧\n       ✦     ✧\n         ✦","Tommy's constellation  ✨"};
        int[] count={0};sky.setText(stars[0]);sky.setOnClickListener(v->sky.setText(stars[Math.min(++count[0],stars.length-1)]));
        new AlertDialog.Builder(this).setTitle("Touch the stars").setView(sky).setPositiveButton("Done",null).show();
    }
}
