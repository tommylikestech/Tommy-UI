# Build Tommy UI from source

> [!CAUTION]
> **Use at your own risk.** Calling emergency services might not work. Calling, texting, cell data and cell service might not work. This operating system is very unstable and might break. Flash at your own risk. We do not guarantee that anything works, and we are not responsible if you break your device.

Choose the source revision for the build you want.

| Revision | Result |
| --- | --- |
| [v1.003](https://github.com/tommylikestech/Tommy-UI/tree/v1.003) | Original Android 16 image flashed on the XT2413-2 |
| `v1.100` | Archived v1.1 Classic image, confirmed running on the retail XT2413-2 |
| `v1.101` | Classic v1.1.1 with a recommended USB path and experimental OTA previews |
| [main](https://github.com/tommylikestech/Tommy-UI/tree/main) | Development build with the wireless update page and boot check |

## Prepare the host

Use x86-64 Linux with Git, Repo, Python 3.11 or later, e2fsprogs, ImageMagick, and the Android build dependencies. Keep the Android source tree on a Linux filesystem and allow several hundred gigabytes for source and build output. [Android host requirements](https://source.android.com/docs/setup/start/requirements)

## Build the image

Clone Tommy UI and choose your revision before starting the AOSP checkout. For Classic v1.1.1, run `git checkout v1.101`. For the original release, run `git checkout v1.003` inside the clone.

```bash
git clone https://github.com/tommylikestech/Tommy-UI.git
cd Tommy-UI
```

Run the following commands from that repository:

```bash
TOMMY_SOURCE="$PWD"
mkdir -p "$HOME/aosp-tommy"
cd "$HOME/aosp-tommy"
repo init -u https://android.googlesource.com/platform/manifest \
    -b android-security-16.0.0_r8 --depth=1
repo sync -c -j4 --fail-fast
python3 "$TOMMY_SOURCE/tools/prepare.py" "$PWD" --check
bash "$TOMMY_SOURCE/tools/build.sh" "$PWD"
python3 "$TOMMY_SOURCE/tools/verify_export.py" "$PWD"
```

The build script verifies the upstream revision, applies the Tommy UI changes, and builds `aosp_arm64-bp2a-userdebug`. Set `TOMMYUI_JOBS` to change parallelism. The verifier checks the ext4 image, app signatures, and build identity before writing the raw system image, image ZIP, build report, and SHA-256 checksums to the Tommy UI repository’s `dist/` directory.

## Check your output

The original **v1.003 tag** produced a system image with this SHA-256:

```text
58672f248a7491fa026a8343f845bd648d31eb9536dd9f2481b55745d1e7d76f
```

The development branch has its own build identity in [release.json](../release.json). For the locally built v1.002 → v1.003 USB upgrade, use the [device and installation instructions](../updater/FLASH-AND-STORAGE.txt).

## Where to make changes

| Location | Contents |
| --- | --- |
| [overlay](../overlay/) | New Android sources, device configuration and artwork |
| [patches](../patches/) | Changes to existing AOSP files |
| [files.json](../files.json) | Expected hashes before and after applying changes |
| [manifests/aosp.xml](../manifests/aosp.xml) | Resolved AOSP project revisions |
| [Contributing](../CONTRIBUTING.md) | Editing guidance and host tests |
| [Publishing a wireless update](../ota/README.md) | OTA package preparation, feed signing and release checks |
