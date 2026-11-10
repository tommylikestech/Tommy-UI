# Update Tommy UI Classic

**USB is the recommended way to update.** Wireless updates are experimental and can fail during installation, restart or snapshot merge. Back up your phone and adopted SD card before either method. Calling, texting and emergency calls may fail on this ROM.

| Installed build on a retail XT2413-2 | Direct path to v1.1.1 |
| --- | --- |
| Tommy UI v1.002 | USB |
| Original Tommy UI v1.003 | USB or opt-in OTA preview |
| Tommy UI Classic v1.1 | USB or opt-in OTA preview |

You can skip intermediate releases. These paths require the matching retail vendor firmware, unlocked bootloader and original source image. Each OTA has a different payload because it starts from a different system image.

## USB installation

1. Install Android Platform Tools and Python 3.11 or later. Enable USB debugging, connect and unlock the phone, and accept the debugging prompt. Run `adb devices` to see the serial. [Android Platform Tools](https://developer.android.com/tools/releases/platform-tools)
2. Download the **system-image ZIP**, **SHA256SUMS.txt** and **classic_usb.py** from the v1.1.1 release. In PowerShell, run `Get-FileHash -Algorithm SHA256 Tommy-UI-v1.1.1-Classic-system-image.zip`. On Linux use `sha256sum`. Compare the result with the published checksum. Extract the ZIP and put the helper alongside the image and build report. [v1.1.1 release files](https://github.com/tommylikestech/Tommy-UI/releases/tag/v1.101)
3. Run the following from that directory, replacing SERIAL with the value from step 1. The first command checks the device, slot, installed system hash and downloaded image. The second enters fastbootd and writes the system partition on the current slot.

```text
python classic_usb.py --serial SERIAL --image Tommy-UI-v1.1.1-Classic-Android16-arm64-system.img
python classic_usb.py --serial SERIAL --image Tommy-UI-v1.1.1-Classic-Android16-arm64-system.img --install
```

Keep the cable connected through restart. Unlock Android and check **Settings > About Tommy UI**. The expected build is `TommyUI.v1.101.dev.20261109`. If the helper reports a different source, a pending snapshot, or a slot mismatch, stop and keep its exact message. If fastboot reports a failed write, keep the phone connected in fastbootd and retain the log.

## Wireless preview

1. On original v1.003 or Classic v1.1, install **Tommy-Settings-Classic-Preview.apk** from the v1.1.1 release once. It adds the preview option to Settings. Use `adb -s SERIAL install -r --force-uuid internal Tommy-Settings-Classic-Preview.apk`. Choose internal storage even if an SD card is your default.
2. Open **Settings > About Tommy UI > Update Tommy UI**. Enable **Include Classic preview updates**. The page checks a signed GitHub Pages feed for the installed build.
3. Tap **Download and install**, then review the warning. When Android offers **Restart to finish**, tap it. You can leave the page and return to it. If installation fails, record the error code and installed build before retrying. Run the USB check above before switching methods.
4. Verify the version in **About Tommy UI** and open your apps and SD card. The build ID uses `v1.101` for v1.1.1. Successful download and staging still require a successful boot and snapshot merge.

## Older versions

v1.003 and v1.1 are outdated and retained for existing installations and reproducibility. Use v1.1.1 for the current preview. The archived v1.1 release is tagged `v1.100`. [v1.1 archive](https://github.com/tommylikestech/Tommy-UI/releases/tag/v1.100)
