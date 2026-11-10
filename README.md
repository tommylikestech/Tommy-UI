# USE AT YOUR OWN RISK

> [!CAUTION]
> ## **Calling emergency services might not work.**
> ## **Calling and texting might not work.**
> ## **Cell data and cell service might not work.**
> ## **This operating system is very unstable and might break.**
> ## **Flash at your own risk. Use at your own risk.**
> ## **We do not guarantee that anything works.**
> ## **We are not responsible if you break your device.**
>
> Tommy UI is an experimental build with no warranty of any kind. Back up anything you care about before you flash it.

# ![Tommy UI](docs/assets/tommy-ui.png)

Tommy UI brings Android 16 to the **Motorola Moto G Play 2024** and lets you move eligible apps to a microSD card. It’s an experimental community build for the **North American retail XT2413-2** (codename `fogona`).

[Releases](https://github.com/tommylikestech/Tommy-UI/releases) · [Build guide](guides/BUILD.md) · [Contributing](CONTRIBUTING.md)

## Tommy UI Classic v1.1.1

**USB is the recommended way to update.** Classic v1.1.1 moves the clock 4 dp inward and gives Tommy Browser a native start page, loading progress and a navigation menu. The original v1.003 and Classic v1.1 have direct paths to v1.1.1. Wireless installation and restart may fail. Back up the phone and SD card before updating. [USB instructions and wireless preview](guides/CLASSIC-UPDATE.md) · [Release files](https://github.com/tommylikestech/Tommy-UI/releases/tag/v1.101)

Older versions are retained for existing installations. Archived v1.1 is tagged v1.100 and v1.1.1 is tagged v1.101. The Update Zero and v1.004 trials remain withdrawn. [Release history](WITHDRAWN.md)

## What you can do

- **Choose where apps live.** Tommy Storage can move eligible apps between phone storage and an adopted microSD card. The system APK installer offers a choice when multiple destinations are available. Adopting a card formats and encrypts it for that phone, so copy your files first.
- **Download APKs on the phone.** Tommy Browser saves direct download links to Downloads, where you can open an APK with Android’s installer.
- **Check for system updates.** Settings checks GitHub Pages at boot and on request. The preview offers a signed payload for your installed build. [Update guide](guides/CLASSIC-UPDATE.md)
- **Make yourself at home.** Tommy Home removes the persistent search bar. About phone shows the Motorola hardware model. About Tommy UI shows the OS version and has a constellation Easter egg if you tap the version seven times.

## Build and test status

The original v1.003 system image passed verification and was flashed on the XT2413-2. The build uses AOSP development keys and declares the **2025-08-05** Android security patch. On-device confirmation of the v1.003 build, corrected SD totals, browser APK downloads, and an end-to-end wireless OS upgrade remains pending.

For build commands, the original image checksum, and a map of the source tree, see the [build guide](guides/BUILD.md). Tommy UI additions use Apache License 2.0. Upstream components retain their own terms. [License](LICENSE) · [Third-party notices](NOTICE)
