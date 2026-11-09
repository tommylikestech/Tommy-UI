# Tommy UI

Tommy UI v1.003 is an experimental Android 16 system image for the Motorola Moto G Play 2024, North America retail model **XT2413-2** (device codename **fogona**). It keeps the AOSP interface and adds controls for using a microSD card as app storage.

| On the phone | What it does |
| --- | --- |
| Settings → Storage | Shows usage for the selected filesystem. Tommy Storage opens Android’s card setup and migration screens and moves eligible apps between internal storage and an adopted card. Adopting a card formats it and encrypts it for that phone. |
| APK installation | The system installer offers a destination when multiple eligible volumes are available. Stores using their own installation sessions can trigger a storage notification after installation. |
| About phone / About Tommy UI | Shows Motorola hardware information separately from the OS version. Tap the Tommy UI version seven times to open the constellation. |
| Tommy Home and Tommy Browser | The launcher omits the persistent search bar. The browser saves direct web downloads, including APKs, to Downloads for opening with the system installer. |
| Wireless update | About Tommy UI → Update Tommy UI checks the signed GitHub Pages feed on demand, and a boot job checks when connected. Compatible newer releases offer a download from GitHub Releases and installation by Android’s update engine. [Release and installation details](ota/README.md) |
| USB update | The Windows updater checks the device and starting build before offering the v1.002 → v1.003 update. |

## Build

Use Linux on x86-64 with the Android build dependencies, Repo, Git, Python 3.11 or newer, and e2fsprogs. Keep the AOSP tree on a Linux filesystem. Allow several hundred gigabytes for sources and build output. [AOSP host setup](https://source.android.com/docs/setup/start/requirements)

From the root of this repository, run:

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

The build script checks the manifest commit, applies the source changes, and builds `aosp_arm64-bp2a-userdebug`. Set `TOMMYUI_JOBS` to change parallelism. The verifier reads the resulting ext4 filesystem, checks the embedded apps and build identity, then writes the system image, USB kit, verification report and SHA-256 list to this repository’s `dist/` directory.

## Release status

The [v1.003 tag](https://github.com/tommylikestech/Tommy-UI/tree/v1.003) reproduces the original image. The main branch adds the update client and uses the incremental build number in release.json. The original v1.003 image was built, verified, and flashed to `system_a` on the target phone. Its SHA-256 is `58672f248a7491fa026a8343f845bd648d31eb9536dd9f2481b55745d1e7d76f`. Confirmation of the running v1.003 build, the corrected SD totals on the physical card, and an end-to-end Tommy Browser APK download remains pending. The image uses AOSP development test keys and declares the upstream **2025-08-05** platform security patch. This source release targets developers.

The USB kit targets an unlocked XT2413-2 already running Tommy UI v1.002 on slot A. Read the [USB update instructions](updater/FLASH-AND-STORAGE.txt) before using a locally built kit. The repository contains the GSI changes and build tools. The phone supplies the Motorola vendor firmware and kernel.

## Source and licensing

New Android sources and artwork live in `overlay/`. Changes to existing AOSP text files live in `patches/`, using their checkout paths. `files.json` records both baseline and result hashes. `manifests/aosp.xml` records the resolved upstream project revisions. See [Contributing](CONTRIBUTING.md) for editing and test instructions.

Tommy UI additions use Apache License 2.0. Upstream code retains its original terms, including the Chromium license for Browser2. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
