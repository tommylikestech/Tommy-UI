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
    private boolean bound, applying;
    private int engineState = -1;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle("Update Tommy UI");
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int margin = Math.round(20 * getResources().getDisplayMetrics().density);
        page.setPadding(margin,margin,margin,margin);
        scroll.addView(page); setContentView(scroll);
        TextView installed = new TextView(this);
        installed.setText("Android " + Build.VERSION.RELEASE + "\nInstalled build  " + Build.VERSION.INCREMENTAL);
        page.addView(installed);
        release = new TextView(this); page.addView(release);
        progress = new TextView(this); page.addView(progress);
        check = button(page,"Check for updates",this::refresh);
        install = button(page,"Download and install",this::installOrRestart);
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
        Button b = new Button(this); b.setText(title); page.addView(b);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void refresh() {
        candidate = null;
        updateButton(); check.setEnabled(false); release.setText("Checking for updates…");
        new Thread(() -> {
            try {
                UpdateFeed r = UpdateCheck.fetch(this);
                String local = UpdateFeed.installed(Build.VERSION.INCREMENTAL);
                boolean newer = UpdateFeed.ordinal(r.version) > UpdateFeed.ordinal(local);
                String text = newer ? "Tommy UI " + r.version + " is available." : "Your build is up to date.";
                text += "\nLatest release  " + r.version + "\n" +
                    ("security".equals(r.kind) ? "Security update" : "Content update") + "\n" + r.summary;
                if (newer && !UpdateCheck.eligible(r)) text += "\nNo wireless package for this device and build.";
                final String result = text;
                runOnUiThread(() -> { if (isDestroyed()) return; candidate=r;
                    release.setText(result); check.setEnabled(true); updateButton(); });
            } catch (Exception e) {
                runOnUiThread(() -> { if (isDestroyed()) return;
                    release.setText("Couldn’t check for updates. " + e.getMessage()); check.setEnabled(true); });
            }
        },"TommyReleaseCheck").start();
    }
    private void updateButton() {
        boolean ready = engineState == 6;
        install.setText(ready ? "Restart to finish" : "Download and install");
        install.setEnabled(bound && (ready || (engineState==0 && !applying && candidate!=null
            && UpdateCheck.eligible(candidate) && UpdateFeed.ordinal(candidate.version) >
                    UpdateFeed.ordinal(UpdateFeed.installed(Build.VERSION.INCREMENTAL)))));
    }
    private void installOrRestart() {
        if (engineState == 6) {
            new AlertDialog.Builder(this).setMessage("Restart to finish the system update?")
                .setNegativeButton("Later",null).setPositiveButton("Restart",(d,i)->
                     getSystemService(PowerManager.class).reboot(null)).show();
            return;
        }
        UpdateFeed r = candidate;
        if (r == null || !UpdateCheck.eligible(r)) return;
        new AlertDialog.Builder(this).setTitle("Install Tommy UI " + r.version + "?")
            .setMessage(r.summary + "\nDownload size: " + android.text.format.Formatter.formatFileSize(this,r.size))
            .setNegativeButton("Cancel",null).setPositiveButton("Download and install",(d,i)-> {
                if (engineState!=0 || applying) return;
                applying=true; updateButton(); progress.setText("Starting download…");
                try { engine.applyPayload(r.url,0,r.size,new String[]{"FILE_HASH="+r.fileHash,
                        "FILE_SIZE="+r.size,"METADATA_HASH="+r.metadataHash,"METADATA_SIZE="+r.metadataSize}); }
                catch (RuntimeException e) { applying=false; progress.setText("Update failed: "+e.getMessage()); updateButton(); }
            }).show();
    }
    private final UpdateEngineCallback callback = new UpdateEngineCallback() {
        public void onStatusUpdate(int status,float percent) {
            engineState=status;
            if (status==6) progress.setText("Ready to restart.");
            else if (status>0) progress.setText(String.format(Locale.getDefault(),"System update · %d%%",Math.round(100*percent)));
            updateButton();
        }
        public void onPayloadApplicationComplete(int error) {
            applying=false;
            if (error!=0) progress.setText("Android could not install the update (code "+error+").");
            updateButton();
        }
    };
    @Override protected void onDestroy() { if (bound) engine.unbind(); super.onDestroy(); }
}
