package com.tommyui.storage;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.UserManager;
import android.os.storage.DiskInfo;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.text.format.Formatter;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class StorageActions {
    private final Activity activity;
    private final StorageManager storage;

    StorageActions(Activity activity) {
        this.activity = activity;
        storage = activity.getSystemService(StorageManager.class);
    }

    void formatCard() {
        try {
            UserManager users = activity.getSystemService(UserManager.class);
            if (!users.isAdminUser() || users.hasUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA)) {
                message("Card setup is unavailable for this user.");
                return;
            }
            List<DiskInfo> cards = new ArrayList<>();
            for (DiskInfo disk : storage.getDisks()) if (disk.isSd()) cards.add(disk);
            if (cards.isEmpty()) { message("Insert a microSD card to set it up."); return; }
            String[] names = cards.stream().map(d -> d.getDescription() + " · "
                    + Formatter.formatFileSize(activity, d.size)).toArray(String[]::new);
            new AlertDialog.Builder(activity).setTitle("Set up which SD card?")
                    .setItems(names, (dialog, index) -> openSetup(cards.get(index)))
                    .setNegativeButton(android.R.string.cancel, null).show();
        } catch (RuntimeException error) { message(error.getMessage()); }
    }

    private void openSetup(DiskInfo card) {
        try {
            DiskInfo live = storage.findDiskById(card.getId());
            if (live == null || !live.isSd() || live.size != card.size) {
                message("The card changed. Open card setup again."); return;
            }
            VolumeInfo primary = activity.getPackageManager().getPrimaryStorageCurrentVolume();
            if (primary != null && Objects.equals(primary.getDiskId(), live.getId())) {
                message("Move shared photos and files back to phone storage before formatting this card.");
                return;
            }
            Intent setup = wizard("StorageWizardInit");
            setup.putExtra(DiskInfo.EXTRA_DISK_ID, live.getId());
            activity.startActivity(setup);
        } catch (RuntimeException error) { message(error.getMessage()); }
    }

    void sharedStorage() {
        try {
            if (!activity.getSystemService(UserManager.class).isAdminUser()) {
                message("Open these controls as the phone owner."); return;
            }
            VolumeInfo current = activity.getPackageManager().getPrimaryStorageCurrentVolume();
            List<VolumeInfo> options = sharedCandidates();
            if (current == null) { message("Android cannot read the current shared storage."); return; }
            options.removeIf(v -> v.getId().equals(current.getId()));
            if (options.isEmpty()) {
                message("Shared storage is on " + name(current) + ". Set up an SD card as phone storage to add a destination.");
                return;
            }
            new AlertDialog.Builder(activity).setTitle("Store photos and files on")
                    .setItems(options.stream().map(this::name).toArray(String[]::new),
                            (dialog, index) -> openMigration(options.get(index)))
                    .setNegativeButton(android.R.string.cancel, null).show();
        } catch (RuntimeException error) { message(error.getMessage()); }
    }

    private List<VolumeInfo> sharedCandidates() {
        List<VolumeInfo> options = new ArrayList<>();
        for (VolumeInfo v : activity.getPackageManager().getPrimaryStorageCandidateVolumes()) {
            if (v.isMountedWritable() && v.getType() == VolumeInfo.TYPE_PRIVATE
                    && (VolumeInfo.ID_PRIVATE_INTERNAL.equals(v.getId())
                    || (v.getDisk() != null && v.getDisk().isSd()))) options.add(v);
        }
        return options;
    }

    private void openMigration(VolumeInfo selected) {
        try {
            for (VolumeInfo v : sharedCandidates()) {
                if (!v.getId().equals(selected.getId()) || !Objects.equals(v.getFsUuid(), selected.getFsUuid())) continue;
                Intent migrate = wizard("StorageWizardMigrateConfirm");
                migrate.putExtra(VolumeInfo.EXTRA_VOLUME_ID, v.getId());
                migrate.putExtra("com.tommyui.storage.TARGET_UUID", v.getFsUuid());
                activity.startActivity(migrate);
                return;
            }
            message("The selected storage is no longer mounted.");
        } catch (RuntimeException error) { message(error.getMessage()); }
    }

    private String name(VolumeInfo v) {
        return VolumeInfo.ID_PRIVATE_INTERNAL.equals(v.getId()) ? "Phone storage"
                : "SD card · " + storage.getBestVolumeDescription(v);
    }

    void files() {
        try {
            activity.startActivity(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_FILES));
        } catch (RuntimeException error) { message(error.getMessage()); }
    }

    private Intent wizard(String screen) {
        return new Intent().setClassName("com.android.settings", "com.android.settings.deviceinfo." + screen);
    }

    private void message(String text) {
        Toast.makeText(activity, text == null ? "Storage unavailable" : text, Toast.LENGTH_LONG).show();
    }
}
