package com.tommyui.storage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A platform-signed, user-operated front end to Android's package move service. */
public final class MainActivity extends Activity {
    private static final String FORCE = "force_allow_on_external";
    private static final String[] REQUIRED_PERMISSIONS = {
        "android.permission.MOVE_PACKAGE", "android.permission.MOUNT_UNMOUNT_FILESYSTEMS",
        "android.permission.WRITE_SECURE_SETTINGS", "android.permission.MANAGE_USERS",
        "android.permission.INTERACT_ACROSS_USERS_FULL"
    };
    private int ink;
    private int muted;
    private int green;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<AppRow> apps = new ArrayList<>();
    private PackageManager pm;
    private StorageManager storage;
    private SharedPreferences preferences;
    private TextView storageTitle, storageNote, listNote, resultText, progressText;
    private LinearLayout appList, progressCard;
    private ProgressBar progress;
    private Switch forceSwitch;
    private EditText search;
    private boolean access, updatingSwitch, startingMove, destroyed, resumed;
    private int generation;
    private final Runnable poll = this::pollMove;

    private static final class AppRow {
        ApplicationInfo info;
        String label, currentName;
        android.graphics.drawable.Drawable icon;
        List<VolumeInfo> targets;
        AppRow(ApplicationInfo i, String l, String c, List<VolumeInfo> t) {
            info = i; label = l; currentName = c; targets = t;
        }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ink = getColor(R.color.classic_ink);
        muted = getColor(R.color.classic_muted);
        green = getColor(R.color.classic_accent);
        pm = getPackageManager();
        storage = getSystemService(StorageManager.class);
        preferences = getSharedPreferences("moves", MODE_PRIVATE);
        // Effective grants are authoritative. A privileged system app can legitimately
        // hold these permissions without sharing the platform certificate.
        access = true;
        for (String permission : REQUIRED_PERMISSIONS)
            access &= checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
        if (access && Settings.Global.getString(getContentResolver(), FORCE) == null) {
            // Initialize a missing setting only; preserve an explicit user choice of off.
            Settings.Global.putInt(getContentResolver(), FORCE, 1);
        }
        buildScreen();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(getColor(R.color.classic_page));
        LinearLayout page = column();
        page.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(page);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        setContentView(scroll);
        page.addView(text("TOMMY UI CLASSIC  /  v1.1", 12, green, true));
        TextView title = text("Tommy Storage", 30, ink, true);
        title.setPadding(0, dp(8), 0, dp(8)); page.addView(title);
        page.addView(text("Choose where your apps live.", 16, muted, false));

        LinearLayout storageBox = card(page);
        storageTitle = text("Checking storage…", 20, ink, true); storageBox.addView(storageTitle);
        storageNote = text("", 14, muted, false); storageBox.addView(storageNote);
        StorageActions actions = new StorageActions(this);
        storageBox.addView(button("Set up or format SD card", actions::formatCard));
        storageBox.addView(button("Photos and files location", actions::sharedStorage));
        storageBox.addView(text("Changing the location moves existing shared files and sets the default for apps that save there.", 14, muted, false));
        storageBox.addView(button("Browse photos and files", actions::files));
        storageBox.addView(button("Open storage settings", () -> openSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));

        LinearLayout control = card(page);
        forceSwitch = new Switch(this);
        forceSwitch.setText("Allow more apps on SD"); forceSwitch.setTextSize(17);
        forceSwitch.setTextColor(ink); forceSwitch.setPadding(0, dp(8), 0, dp(8));
        control.addView(forceSwitch);
        control.addView(button("New app storage alerts", () -> {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                    && !getPreferences(MODE_PRIVATE).getBoolean("notification_asked", false)) {
                getPreferences(MODE_PRIVATE).edit().putBoolean("notification_asked", true).apply();
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 10);
            } else {
                Intent settings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                settings.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(settings);
            }
        }));
        control.addView(text("Override an app’s preference to stay on the phone. System apps and protected apps still cannot move.", 14, muted, false));
        forceSwitch.setOnCheckedChangeListener((view, enabled) -> {
            if (updatingSwitch) return;
            syncSwitch();
            if (enabled) {
                new AlertDialog.Builder(this).setTitle("Allow more apps on SD?")
                    .setMessage("This changes Android’s app-storage setting for all apps. Some apps, widgets or background services may work poorly on a card. Android’s system and administrator protections remain in place.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Allow", (d, w) -> setForce(true)).show();
            } else setForce(false);
        });

        LinearLayout appearance = card(page);
        appearance.addView(text("Make it yours", 19, ink, true));
        appearance.addView(button(getString(R.string.classic_name), () -> startActivity(new Intent(this, AboutTommy.class))));
        appearance.addView(button("Wallpaper and colors", () -> openSettings(Intent.ACTION_SET_WALLPAPER)));
        appearance.addView(button("Animation speed", this::chooseAnimationSpeed));

        progressCard = card(page);
        progressText = text("", 16, ink, true); progressCard.addView(progressText);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100); progressCard.addView(progress);
        progressCard.addView(text("Android handles the transfer. Keep the card inserted until it finishes. You can leave this app and reopen it to check progress.", 14, muted, false));
        progressCard.setVisibility(View.GONE);
        resultText = text("", 15, green, true); resultText.setPadding(0, dp(14), 0, 0);
        page.addView(resultText);

        TextView appsHeading = text("Your apps", 22, ink, true);
        appsHeading.setPadding(0, dp(26), 0, dp(8)); page.addView(appsHeading);
        search = new EditText(this); search.setSingleLine(true); search.setHint("Search apps");
        search.setTextSize(16); search.setTextColor(ink); search.setHintTextColor(muted);
        page.addView(search, new LinearLayout.LayoutParams(-1, dp(56)));
        if (NewAppReceiver.ACTION_REVIEW.equals(getIntent().getAction())
                && getIntent().getData() != null && "package".equals(getIntent().getData().getScheme())) {
            search.setText(getIntent().getData().getSchemeSpecificPart());
        }
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterApps(); }
            public void afterTextChanged(Editable e) {}
        });
        listNote = text("", 14, muted, false); page.addView(listNote);
        page.addView(button("Refresh apps and storage", this::refresh));
        appList = column(); page.addView(appList);
        TextView footer = text("Only adopted SD cards can hold apps. Setting up a card as phone storage formats and encrypts it. Confirm formatting in Android’s card setup to erase the selected card.", 13, muted, false);
        footer.setPadding(0, dp(20), 0, dp(12)); page.addView(footer);
        if (!access) {
            storageTitle.setText("System permission unavailable");
            StringBuilder missing = new StringBuilder();
            for (String permission : REQUIRED_PERMISSIONS) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED)
                    missing.append("\n• ").append(permission.replace("android.permission.", ""));
            }
            storageNote.setText("Android has not granted:" + missing
                    + "\n\nTommy Storage requires the privileged app included with Tommy UI. If this still appears after the update, copy the diagnostics below so the exact cause can be checked.");
            forceSwitch.setEnabled(false);
        }
        page.addView(button("Copy support diagnostics", this::copyDiagnostics));
    }

    private void copyDiagnostics() {
        StringBuilder details = new StringBuilder("Tommy Storage 1.1\nAndroid: ")
                .append(Build.VERSION.RELEASE).append(" / SDK ").append(Build.VERSION.SDK_INT)
                .append("\nBuild: ").append(Build.DISPLAY).append("\nType: ").append(Build.TYPE)
                .append("\nSystem app: ").append((getApplicationInfo().flags & ApplicationInfo.FLAG_SYSTEM) != 0)
                .append("\nPlatform signature matches: ")
                .append(pm.checkSignatures("android", getPackageName()) == PackageManager.SIGNATURE_MATCH);
        for (String permission : REQUIRED_PERMISSIONS) details.append('\n').append(permission)
                .append(": ").append(checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED ? "granted" : "missing");
        android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Tommy UI diagnostics", details));
        new AlertDialog.Builder(this).setTitle("Diagnostics copied").setMessage(details)
                .setPositiveButton("OK", null).show();
    }

    private void chooseAnimationSpeed() {
        if (!access) { copyDiagnostics(); return; }
        new AlertDialog.Builder(this).setTitle("Animation speed")
                .setItems(new String[]{"Relaxed · 1×", "Tommy default · 0.75×", "Quick · 0.5×"}, (d, which) -> {
                    float scale = new float[]{1f, .75f, .5f}[which];
                    try {
                        for (String key : new String[]{"window_animation_scale", "transition_animation_scale", "animator_duration_scale"})
                            if (!Settings.Global.putFloat(getContentResolver(), key, scale))
                                throw new IllegalStateException("Android did not save the animation setting.");
                        resultText.setText("Animation timing updated. This changes transition duration, not CPU speed.");
                    } catch (RuntimeException e) { showError("Could not change animations", e); }
                }).setNegativeButton("Cancel", null).show();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (!access) return;
        if (pendingId() >= 0 && preferences.getInt("boot", -1) != bootCount()) {
            finishTracking("The phone restarted. Check each app’s current location below; no move was retried.");
        }
        resultText.setText(preferences.getString("last_result", ""));
        refresh(); pollMove();
    }
    @Override protected void onPause() { resumed = false; super.onPause(); handler.removeCallbacks(poll); }
    @Override protected void onDestroy() {
        destroyed = true; handler.removeCallbacks(poll); worker.shutdown(); super.onDestroy();
    }

    private int bootCount() { return Settings.Global.getInt(getContentResolver(), "boot_count", 0); }
    private int pendingId() { return preferences.getInt("move_id", -1); }
    private boolean busy() { return startingMove || pendingId() >= 0; }
    private boolean forceEnabled() { return Settings.Global.getInt(getContentResolver(), FORCE, 0) != 0; }
    private void syncSwitch() {
        updatingSwitch = true;
        forceSwitch.setChecked(forceEnabled()); forceSwitch.setEnabled(access && !busy());
        updatingSwitch = false;
    }
    private void setForce(boolean enabled) {
        if (!access || busy()) return;
        try {
            if (!Settings.Global.putInt(getContentResolver(), FORCE, enabled ? 1 : 0)
                    || forceEnabled() != enabled) throw new IllegalStateException("Android did not save the setting.");
            resultText.setText(enabled ? "More apps can now be offered an SD destination." : "Apps’ normal storage preferences are restored. Existing apps were not moved.");
            refresh();
        } catch (RuntimeException e) { showError("Could not change the setting", e); syncSwitch(); }
    }

    private void refresh() {
        if (!access || destroyed) return;
        syncSwitch();
        final int request = ++generation;
        listNote.setText("Checking installed apps…");
        worker.execute(() -> {
            try {
                List<VolumeInfo> volumes = storage.getVolumes();
                boolean adopted = false, portable = false, unmounted = false;
                for (VolumeInfo v : volumes) {
                    if (!isSd(v)) continue;
                    if (v.getType() == VolumeInfo.TYPE_PRIVATE) {
                        if (v.isMountedWritable()) adopted = true; else unmounted = true;
                    } else if (v.getType() == VolumeInfo.TYPE_PUBLIC) portable = true;
                }
                String heading = adopted ? "SD app storage is ready" : portable ? "Set up your SD card" : unmounted ? "SD app storage is unavailable" : "No SD app storage yet";
                String note = adopted ? "Choose an app below to move it to the SD card or back to the phone."
                        : portable ? "This card is portable storage. Tap Set up or format SD card and choose phone storage. That setup erases the card."
                        : unmounted ? "Unlock the phone and check that your adopted card is inserted and mounted."
                        : "Insert a microSD card, then set it up as phone/internal storage in Android’s storage settings.";
                if (adopted) {
                    long total = 0, available = 0;
                    for (VolumeInfo v : volumes) {
                        if (v.getType() != VolumeInfo.TYPE_PRIVATE || !v.isMountedWritable()
                                || (!internal(v) && !isSd(v)) || v.getPath() == null) continue;
                        total += v.getPath().getTotalSpace();
                        available += v.getPath().getUsableSpace();
                    }
                    if (total > 0) note = android.text.format.Formatter.formatFileSize(this, available)
                            + " available across " + android.text.format.Formatter.formatFileSize(this, total)
                            + " of phone and SD app storage. Each app uses one location. " + note;
                }
                final String storageSummary = note;
                List<AppRow> rows = new ArrayList<>();
                for (ApplicationInfo info : pm.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS)) {
                    if (protectedApp(info)) continue;
                    VolumeInfo current = pm.getPackageCurrentVolume(info);
                    AppRow row = new AppRow(info, info.loadLabel(pm).toString(), volumeName(current), targets(info, current));
                    try { row.icon = info.loadIcon(pm); } catch (RuntimeException ignored) { }
                    rows.add(row);
                }
                rows.sort(Comparator.comparing(r -> r.label.toLowerCase(Locale.ROOT)));
                runOnUiThread(() -> {
                    if (destroyed || request != generation) return;
                    apps.clear(); apps.addAll(rows); storageTitle.setText(heading); storageNote.setText(storageSummary); renderApps();
                });
            } catch (RuntimeException | LinkageError e) {
                runOnUiThread(() -> {
                    if (destroyed || request != generation) return;
                    listNote.setText("Android could not provide the app-storage list.");
                    resultText.setText("Storage access failed: " + explanation(e));
                });
            }
        });
    }

    private boolean protectedApp(ApplicationInfo i) {
        return MoveRules.protectedApp((i.flags & ApplicationInfo.FLAG_SYSTEM) != 0,
                (i.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0, getPackageName().equals(i.packageName));
    }
    private boolean isSd(VolumeInfo v) { return v.getDisk() != null && v.getDisk().isSd(); }
    private boolean internal(VolumeInfo v) { return v != null && VolumeInfo.ID_PRIVATE_INTERNAL.equals(v.getId()); }
    private String volumeName(VolumeInfo v) {
        if (v == null) return "Location unavailable";
        if (internal(v)) return "Phone storage";
        String name = storage.getBestVolumeDescription(v);
        return "SD / external storage" + (name == null ? "" : " · " + name);
    }
    private List<VolumeInfo> targets(ApplicationInfo info, VolumeInfo current) {
        List<VolumeInfo> result = new ArrayList<>();
        for (VolumeInfo v : pm.getPackageCandidateVolumes(info)) {
            if (MoveRules.destinationAllowed(current != null && Objects.equals(v.getId(), current.getId()),
                    v.isMountedWritable(), v.getType() == VolumeInfo.TYPE_PRIVATE, internal(v), isSd(v))) result.add(v);
        }
        return result;
    }
    private void renderApps() {
        if (appList == null) return;
        appList.removeAllViews();
        boolean allowMore = forceEnabled();
        for (AppRow app : apps) {
            LinearLayout box = card(appList);
            box.setTag((app.label + "\n" + app.info.packageName).toLowerCase(Locale.ROOT));
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
            iconParams.setMarginEnd(dp(12)); row.addView(icon, iconParams);
            LinearLayout labels = column(); labels.addView(text(app.label, 17, ink, true));
            labels.addView(text(app.currentName, 13, muted, false)); row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            box.addView(row);
            TextView pkg = text(app.info.packageName, 12, muted, false); pkg.setTextIsSelectable(true); box.addView(pkg);
            if (app.targets.isEmpty()) {
                box.addView(text(allowMore ? "Android is not offering another usable storage destination for this app."
                        : "No move available. Set up an SD card, or try ‘Allow more apps on SD’.", 13, muted, false));
            } else {
                Button move = button("Change storage", () -> chooseDestination(app));
                move.setEnabled(!busy()); box.addView(move);
            }
            box.addView(button("App info", () -> {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.info.packageName));
                try { startActivity(i); } catch (RuntimeException e) { showError("Cannot open app info", e); }
            }));
        }
        filterApps();
    }

    private void filterApps() {
        if (appList == null) return;
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (int i = 0; i < appList.getChildCount(); i++) {
            View row = appList.getChildAt(i);
            boolean matches = ((String) row.getTag()).contains(query);
            row.setVisibility(matches ? View.VISIBLE : View.GONE);
            if (matches) shown++;
        }
        listNote.setText(apps.isEmpty() ? "No downloaded apps to move yet. System apps and Tommy Storage are excluded."
                : shown == 0 ? "No apps match your search." : shown + " apps · system apps excluded");
    }

    private void chooseDestination(AppRow app) {
        if (busy() || !access) return;
        try {
            ApplicationInfo fresh = pm.getApplicationInfo(app.info.packageName, 0);
            if (protectedApp(fresh)) return;
            List<VolumeInfo> options = targets(fresh, pm.getPackageCurrentVolume(fresh));
            if (options.isEmpty()) { refresh(); resultText.setText("That destination is no longer available."); return; }
            String[] names = new String[options.size()];
            for (int n = 0; n < names.length; n++) names[n] = volumeName(options.get(n));
            new AlertDialog.Builder(this).setTitle("Move " + app.label + " to…")
                .setItems(names, (d, which) -> confirmMove(app, options.get(which)))
                .setNegativeButton("Cancel", null).show();
        } catch (Exception | LinkageError e) { showError("Cannot offer a move", e); }
    }
    private void confirmMove(AppRow app, VolumeInfo target) {
        new AlertDialog.Builder(this).setTitle("Move " + app.label + "?")
            .setMessage("Destination: " + volumeName(target) + "\n\nAndroid will close this app while transferring it. Keep the SD card inserted. Some apps may not work correctly from SD storage.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Move app", (d, w) -> startMove(app, target.getId())).show();
    }
    private void startMove(AppRow app, String targetId) {
        if (busy() || !access) return;
        startingMove = true; syncSwitch(); renderApps();
        resultText.setText("Requesting move…");
        worker.execute(() -> {
            try {
                ApplicationInfo fresh = pm.getApplicationInfo(app.info.packageName, 0);
                if (protectedApp(fresh)) throw new IllegalStateException("This app is protected from moving.");
                VolumeInfo target = null;
                for (VolumeInfo v : targets(fresh, pm.getPackageCurrentVolume(fresh)))
                    if (Objects.equals(v.getId(), targetId)) target = v;
                if (target == null) throw new IllegalStateException("The destination is no longer available.");
                int id = pm.movePackage(fresh.packageName, target);
                if (id < 0) throw new IllegalStateException(MoveRules.failure(id));
                // The system owns the move; persist its ID so reopening the app resumes tracking.
                preferences.edit().putInt("move_id", id).putInt("boot", bootCount())
                    .putString("move_label", app.label).putString("move_package", fresh.packageName)
                    .putString("move_target", volumeName(target)).putString("last_result", "").commit();
                runOnUiThread(() -> {
                    startingMove = false;
                    if (!destroyed) { resultText.setText(""); syncSwitch(); pollMove(); }
                });
            } catch (Exception | LinkageError e) {
                runOnUiThread(() -> {
                    startingMove = false;
                    if (!destroyed) { showError("Move could not start", e); refresh(); }
                });
            }
        });
    }
    private void pollMove() {
        handler.removeCallbacks(poll);
        if (!access || destroyed || !resumed) return;
        int id = pendingId();
        if (id < 0) { progressCard.setVisibility(View.GONE); return; }
        progressCard.setVisibility(View.VISIBLE);
        try {
            int status = pm.getMoveStatus(id);
            String label = preferences.getString("move_label", "App");
            if (PackageManager.isMoveStatusFinished(status)) {
                String outcome = status == PackageManager.MOVE_SUCCEEDED
                    ? label + " moved to " + preferences.getString("move_target", "the selected storage") + "."
                    : label + ": " + MoveRules.failure(status);
                finishTracking(outcome); refresh(); return;
            }
            progress.setIndeterminate(status <= 0);
            progress.setProgress(Math.max(0, Math.min(100, status)));
            progressText.setText("Moving " + label + (status > 0 ? " · " + status + "%" : "…"));
            handler.postDelayed(poll, 1000);
        } catch (RuntimeException | LinkageError e) {
            progressText.setText("Progress unavailable. Android may still be moving the app. Reopen Tommy Storage to check again.");
            progress.setIndeterminate(true);
        }
    }
    private void finishTracking(String message) {
        preferences.edit().remove("move_id").remove("move_label").remove("move_package")
            .remove("move_target").remove("boot").putString("last_result", message).apply();
        progressCard.setVisibility(View.GONE); resultText.setText(message); syncSwitch();
    }
    private void openSettings(String action) {
        try { startActivity(new Intent(action)); }
        catch (RuntimeException e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (RuntimeException second) { showError("Cannot open settings", second); }
        }
    }
    private String explanation(Throwable e) {
        String m = e.getMessage(); return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }
    private void showError(String title, Throwable error) {
        if (!destroyed) new AlertDialog.Builder(this).setTitle(title).setMessage(explanation(error))
                .setPositiveButton("OK", null).show();
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout v = column(); v.setPadding(dp(18), dp(18), dp(18), dp(14));
        GradientDrawable bg = new GradientDrawable(); bg.setColor(getColor(R.color.classic_panel)); bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), getColor(R.color.classic_stroke)); v.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(16);
        parent.addView(v, params); return v;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setPadding(0, dp(4), 0, dp(4)); t.setLineSpacing(dp(2), 1);
        if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }
    private Button button(String value, Runnable click) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setTextColor(green);
        b.setMinHeight(dp(48)); b.setOnClickListener(v -> click.run()); return b;
    }
}
