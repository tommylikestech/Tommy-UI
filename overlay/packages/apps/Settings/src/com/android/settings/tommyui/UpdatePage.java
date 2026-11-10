package com.android.settings.tommyui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.*;
import android.widget.*;
import java.util.Locale;

public final class UpdatePage extends Activity {
    private TextView release, progress;
    private Button install, check;
    private UpdateFeed candidate;
    private UpdateEngine engine;
    private boolean bound, applying, trial, selecting;
    private int requestId;
    private int engineState = -1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Update Tommy UI");
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int margin = Math.round(20 * getResources().getDisplayMetrics().density);
        page.setPadding(margin,margin,margin,margin);
        scroll.setFillViewport(true);
        scroll.addView(page); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()
                    | android.view.WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        TextView installed = new TextView(this);
        installed.setText("Android " + Build.VERSION.RELEASE + "\nInstalled build  " + Build.VERSION.INCREMENTAL);
        android.graphics.drawable.GradientDrawable cover = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[]{0xff254e40, 0xff0f2922});
        cover.setCornerRadius(margin);
        installed.setBackground(cover);
        installed.setTextColor(0xfff1f8ed);
        installed.setTextSize(18);
        installed.setPadding(margin, margin, margin, margin);
        page.addView(installed);
        release = new TextView(this); release.setTextSize(16); release.setPadding(0, margin, 0, margin / 2); page.addView(release);
        progress = new TextView(this); page.addView(progress);
        TextView warning = new TextView(this);
        warning.setText(UpdateCheck.OTA_NOTICE);
        warning.setPadding(0, margin / 2, 0, margin / 2);
        page.addView(warning);
        button(page,"USB update instructions",() -> {
            String guide = UpdateFeed.REPO + "/blob/main/guides/CLASSIC-UPDATE.md";
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(guide))); }
            catch (android.content.ActivityNotFoundException e) { progress.setText(guide); }
        });
        if (UpdateCheck.previewSupported()) {
            CheckBox preview = new CheckBox(this);
            preview.setText("Include Classic preview updates");
            preview.setChecked(UpdateCheck.previewEnabled(this));
            page.addView(preview);
            preview.setOnCheckedChangeListener((box, enabled) -> {
                android.provider.Settings.Global.putInt(getContentResolver(), UpdateCheck.PREVIEW_SETTING, enabled ? 1 : 0);
                refresh();
            });
        }
        check = button(page,"Check for updates",this::refresh);
        install = button(page,"Download and install",this::installOrRestart);
        if ("TommyUI.v1.003.dev.20261106".equals(Build.VERSION.INCREMENTAL)
                && "XT2413-2".equals(SystemProperties.get("ro.boot.hardware.sku"))) {
            if (android.provider.Settings.Global.getInt(getContentResolver(), "tommyui_ota_trial", 0) == 1) {
                button(page,"Check staged OTA trial",() -> refresh(true));
            }
            Button test = button(page,"Run Update Zero download test",() -> {});
            test.setOnClickListener(v -> runDownloadTest(test));
        }
        button(page,"Check GitHub for more details",() -> {
            String link = candidate == null ? UpdateFeed.REPO+"/releases" :
                    UpdateFeed.REPO+"/releases/tag/"+candidate.version;
            try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(link))); }
            catch (android.content.ActivityNotFoundException e) { progress.setText(link); }
        });
        try {
            engine = new UpdateEngine();
            bound = engine.bind(callback,new Handler(getMainLooper()));
            if (!bound) progress.setText("Android’s update service is unavailable.");
        } catch (RuntimeException e) { progress.setText("Android’s update service is unavailable."); }
        refresh();
    }
    private Button button(LinearLayout page,String title,Runnable action) {
        Button b = new Button(this); b.setText(title); b.setAllCaps(false); page.addView(b);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void runDownloadTest(Button test) {
        test.setEnabled(false);
        progress.setText("Downloading test metadata…");
        new Thread(() -> {
            String result;
            javax.net.ssl.HttpsURLConnection connection = null;
            try {
                String asset = UpdateFeed.REPO + "/releases/download/update-zero-1.0.03/update-zero.metadata";
                connection = (javax.net.ssl.HttpsURLConnection) new java.net.URL(asset).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                if (connection.getResponseCode() != 200) throw new java.io.IOException("Download unavailable");
                byte[] bytes;
                try (java.io.InputStream input = connection.getInputStream()) {
                    bytes = input.readNBytes(449);
                }
                byte[] expected = android.util.Base64.decode("pISuD6DuDosuO70deIVhV+Sb00VIQfS3Z6WgQprpbsQ=", android.util.Base64.DEFAULT);
                if (bytes.length != 448 || !java.security.MessageDigest.isEqual(expected,
                        java.security.MessageDigest.getInstance("SHA-256").digest(bytes))) {
                    throw new java.io.IOException("Test metadata checksum mismatch");
                }
                result = "Update Zero passed the download and checksum test. Your OS is still on v1.003.";
            } catch (Exception e) {
                result = "Update Zero test failed: " + e.getMessage();
            } finally {
                if (connection != null) connection.disconnect();
            }
            final String message = result;
            runOnUiThread(() -> {
                if (!isDestroyed()) { progress.setText(message); test.setEnabled(true); }
            });
        },"TommyOtaPreflight").start();
    }

    private boolean trialPending() {
        return Build.VERSION.INCREMENTAL.equals(getPreferences(MODE_PRIVATE).getString("stagingSource", ""));
    }
    private String verifiedTicket() {
        if (!"TommyUI.v1.003.dev.20261106".equals(Build.VERSION.INCREMENTAL)
                || !"XT2413-2".equals(SystemProperties.get("ro.boot.hardware.sku"))
                || !"_a".equals(SystemProperties.get("ro.boot.slot_suffix"))) return "";
        int boot = android.provider.Settings.Global.getInt(getContentResolver(),
                android.provider.Settings.Global.BOOT_COUNT, -1);
        String ticket = "r2:" + boot;
        String value = android.provider.Settings.Global.getString(getContentResolver(), "tommyui_verified_target");
        return boot >= 0 && (ticket.equals(value) || (ticket + ":selected").equals(value)) ? value : "";
    }
    private TrialFinish finishAction() {
        String ticket = verifiedTicket();
        return TrialFinish.action(trialPending(), !ticket.isEmpty(), ticket.endsWith(":selected")
                || (!ticket.isEmpty() && ticket.equals(getPreferences(MODE_PRIVATE).getString("activationTicket", ""))),
                selecting, engineState);
    }
    private void selectVerifiedTarget() {
        new AlertDialog.Builder(this).setTitle("Select verified v1.004 for next boot?")
            .setMessage(UpdateCheck.NOTICE + "\n\nAndroid will finish the staged update and select slot B. "
                    + "The phone stays on until you choose Restart. First boot and rollback are experimental.")
            .setNegativeButton("Cancel",null).setPositiveButton("I understand. Select slot B",(d,i)-> {
                if (finishAction()!=TrialFinish.SELECT) return;
                String ticket = verifiedTicket();
                if (ticket.isEmpty() || !getPreferences(MODE_PRIVATE).edit()
                        .putString("activationTicket", ticket).commit()) return;
                selecting=true; updateButton();
                progress.setText("Asking Android to finish the verified update…");
                try {
                    engine.setShouldSwitchSlotOnReboot("/data/misc/tommyui-r2-" + ticket.split(":")[1] + ".metadata");
                } catch (RuntimeException e) {
                    selecting=false;
                    getPreferences(MODE_PRIVATE).edit().remove("activationTicket").apply();
                    progress.setText("Could not finish the update: " + e.getMessage()); updateButton();
                }
            }).show();
    }
    private static String displayVersion(String version) {
        return "v1.100".equals(version) ? "v1.1" : "v1.101".equals(version) ? "v1.1.1" : version;
    }
    private void refresh() { refresh(false); }
    private void refresh(boolean testing) {
        final int currentRequest = ++requestId;
        trial = testing;
        candidate = null;
        updateButton(); check.setEnabled(false); release.setText("Checking for updates…");
        new Thread(() -> {
            try {
                UpdateFeed r = UpdateCheck.fetch(this, testing);
                String local = UpdateFeed.installed(Build.VERSION.INCREMENTAL);
                boolean newer = UpdateFeed.ordinal(r.version) > UpdateFeed.ordinal(local);
                String text = newer ? "Tommy UI " + displayVersion(r.version) + " is available." : "Your build is up to date.";
                text += "\nLatest release  " + displayVersion(r.version) + "\n" +
                    ("security".equals(r.kind) ? "Security update" : "Content update") + "\n" + r.summary;
                if (newer && !UpdateCheck.eligible(r)) text += "\nNo wireless package for this device and build.";
                final String result = testing ? "Staged OTA trial\n" + text : text;
                runOnUiThread(() -> { if (isDestroyed() || currentRequest != requestId) return; candidate=r;
                    release.setText(result); check.setEnabled(true); updateButton(); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (isDestroyed() || currentRequest != requestId) return;
                    release.setText("Couldn’t check for updates. " + e.getMessage()); check.setEnabled(true); });
            }
        },"TommyReleaseCheck").start();
    }
    private void updateButton() {
        TrialFinish action = finishAction();
        if (action != TrialFinish.NORMAL) {
            switch (action) {
                case WAIT: install.setText("Trial pending · target audit required"); break;
                case SELECT: install.setText("Finish verified trial"); break;
                case BUSY: install.setText("Checking update engine…"); break;
                case RESTART: install.setText("Restart into v1.004"); break;
                default: break;
            }
            install.setEnabled(bound && (action==TrialFinish.SELECT || action==TrialFinish.RESTART));
            return;
        }
        boolean ready = engineState == 6;
        install.setText(ready ? "Restart to finish" : trial ? "Stage trial update" : "Download and install");
        install.setEnabled(bound && (ready || (engineState==0 && !applying && candidate!=null
            && UpdateCheck.eligible(candidate) && UpdateFeed.ordinal(candidate.version) >
                    UpdateFeed.ordinal(UpdateFeed.installed(Build.VERSION.INCREMENTAL)))));
    }
    private void installOrRestart() {
        TrialFinish action=finishAction();
        if (action==TrialFinish.SELECT) { selectVerifiedTarget(); return; }
        if (action==TrialFinish.WAIT || action==TrialFinish.BUSY) return;
        if (engineState == 6) {
            new AlertDialog.Builder(this).setMessage(UpdateCheck.NOTICE + "\n\nRestart to finish the system update?")
                .setNegativeButton("Later",null).setPositiveButton("I understand. Restart",(d,i)-> {
                    if (engineState==6 && finishAction()==action)
                        getSystemService(PowerManager.class).reboot(null);
                }).show();
            return;
        }
        UpdateFeed r = candidate;
        if (r == null || !UpdateCheck.eligible(r)) return;
        final boolean stage = trial;
        new AlertDialog.Builder(this).setTitle((stage ? "Stage Tommy UI " : "Install Tommy UI ") + displayVersion(r.version) + "?")
            .setMessage(UpdateCheck.OTA_NOTICE + "\n\n" + UpdateCheck.NOTICE + (stage ? "\n\nThis experimental update writes system and boot firmware to slot B. Slot A remains selected for restart. Keep the phone powered until staging completes." : "") + "\n\n" + r.summary + "\nDownload size: " + android.text.format.Formatter.formatFileSize(this,r.size))
            .setNegativeButton("Cancel",null).setPositiveButton("I understand and agree",(d,i)-> {
                if (engineState!=0 || applying || candidate!=r || !UpdateCheck.eligible(r)) return;
                if (stage && !getPreferences(MODE_PRIVATE).edit().putString("stagingSource", Build.VERSION.INCREMENTAL).commit()) {
                    progress.setText("Could not save trial state."); return;
                }
                applying=true; updateButton(); progress.setText("Starting download…");
                try { engine.applyPayload(r.url,0,r.size,new String[]{"FILE_HASH="+r.fileHash,
                        "FILE_SIZE="+r.size,"METADATA_HASH="+r.metadataHash,"METADATA_SIZE="+r.metadataSize,
                        "SWITCH_SLOT_ON_REBOOT="+(stage ? "0" : "1")}); }
                catch (RuntimeException e) { applying=false; getPreferences(MODE_PRIVATE).edit().remove("stagingSource").apply(); progress.setText("Update failed: "+e.getMessage()); updateButton(); }
            }).show();
    }
    private final UpdateEngineCallback callback = new UpdateEngineCallback() {
        public void onStatusUpdate(int status,float percent) {
            engineState=status;
            if (status==6) progress.setText(finishAction()==TrialFinish.RESTART
                    ? "Android is ready to boot the verified update."
                    : trialPending() ? "Trial pending. Verify the target before restarting." : "Ready to restart.");
            else if (status==3) progress.setText(String.format(Locale.getDefault(),"Downloading · %d%%",Math.round(100*percent)));
            else if (status==4) progress.setText("Verifying the update…");
            else if (status==5) progress.setText("Finishing installation…");
            else if (status>0) progress.setText("Android is preparing the update…");
            updateButton();
        }
        public void onPayloadApplicationComplete(int error) {
            applying=false;
            boolean finishing = selecting || (!verifiedTicket().isEmpty() && verifiedTicket().equals(
                    getPreferences(MODE_PRIVATE).getString("activationTicket","")));
            selecting=false;
            if (finishing && error==0) {
                progress.setText("Activation finished. Waiting for Android’s restart-ready status.");
            } else if (finishing && error!=0) {
                getPreferences(MODE_PRIVATE).edit().remove("activationTicket").apply();
                progress.setText("Activation did not finish (code "+error+").");
            } else if (trialPending() && error==UpdateEngine.ErrorCodeConstants.UPDATED_BUT_NOT_ACTIVE) {
                progress.setText("Staging finished. Verify slot B before selecting it for restart.");
            } else if (error!=0) {
                getPreferences(MODE_PRIVATE).edit().remove("stagingSource").apply();
                progress.setText("Android could not install the update (code "+error+").");
            }
            updateButton();
        }
    };
    @Override protected void onDestroy() { if (bound) engine.unbind(); super.onDestroy(); }
}
