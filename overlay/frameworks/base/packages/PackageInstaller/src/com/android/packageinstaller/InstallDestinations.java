package com.android.packageinstaller;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller.SessionParams;
import android.content.pm.PackageManager;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.provider.Settings;
import android.text.format.Formatter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A user choice is valid only for the same new app and the same mounted private volume. */
public final class InstallDestinations {
    private InstallDestinations() {}

    public static final class Option {
        final String packageName;
        final String id;
        final String uuid;
        public final String label;

        Option(String packageName, VolumeInfo volume, String label) {
            this.packageName = packageName;
            this.id = volume.getId();
            this.uuid = volume.getFsUuid();
            this.label = label;
        }
    }

    public static List<Option> list(Context context, SessionParams params) {
        if (params.appPackageName == null || params.sizeBytes < 0) return Collections.emptyList();
        if (params.installLocation != PackageInfo.INSTALL_LOCATION_AUTO
                && params.installLocation != PackageInfo.INSTALL_LOCATION_PREFER_EXTERNAL
                && Settings.Global.getInt(context.getContentResolver(),
                        Settings.Global.FORCE_ALLOW_ON_EXTERNAL, 0) == 0) return Collections.emptyList();
        StorageManager storage = context.getSystemService(StorageManager.class);
        List<VolumeInfo> volumes = storage.getVolumes();
        if (volumes.stream().noneMatch(InstallDestinations::isUsableCard)) return Collections.emptyList();
        try {
            context.getPackageManager().getApplicationInfo(params.appPackageName,
                    PackageManager.MATCH_ANY_USER | PackageManager.MATCH_UNINSTALLED_PACKAGES);
            return Collections.emptyList(); // Updates use Android's existing volume policy.
        } catch (PackageManager.NameNotFoundException newApp) {
            // A new install may offer a choice.
        }
        boolean allowPhone = context.getResources().getBoolean(
                com.android.internal.R.bool.config_allow3rdPartyAppOnInternal);
        List<Option> options = new ArrayList<>();
        boolean hasSd = false;
        for (VolumeInfo volume : volumes) {
            if (!volume.isMountedWritable() || volume.getType() != VolumeInfo.TYPE_PRIVATE
                    || volume.getPath() == null) continue;
            boolean phone = VolumeInfo.ID_PRIVATE_INTERNAL.equals(volume.getId());
            if (phone ? !allowPhone : !isUsableCard(volume)) continue;
            try {
                long available = storage.getAllocatableBytes(storage.getUuidForPath(volume.getPath()));
                if (available < params.sizeBytes) continue;
                String description = storage.getBestVolumeDescription(volume);
                String name = phone ? "Phone storage" : description == null ? "SD card" : "SD card · " + description;
                Option option = new Option(params.appPackageName, volume, name + " · "
                        + Formatter.formatFileSize(context, available) + " available");
                if (phone) options.add(0, option);
                else { options.add(option); hasSd = true; }
            } catch (IOException | IllegalStateException | SecurityException unavailable) {
                // A card may disappear during enumeration.
            }
        }
        return hasSd ? options : Collections.emptyList();
    }

    private static boolean isUsableCard(VolumeInfo volume) {
        return volume.getType() == VolumeInfo.TYPE_PRIVATE && volume.isMountedWritable()
                && volume.getDisk() != null && volume.getDisk().isSd() && volume.getFsUuid() != null;
    }

    public static void apply(Context context, SessionParams params, Option selected)
            throws IOException {
        for (Option current : list(context, params)) {
            if (!current.packageName.equals(selected.packageName) || !current.id.equals(selected.id)
                    || !Objects.equals(current.uuid, selected.uuid)) continue;
            params.volumeUuid = current.uuid;
            params.installFlags |= VolumeInfo.ID_PRIVATE_INTERNAL.equals(current.id)
                    ? PackageManager.INSTALL_INTERNAL : PackageManager.INSTALL_FORCE_VOLUME_UUID;
            return;
        }
        throw new IOException("The selected storage is no longer available for this app.");
    }
}
