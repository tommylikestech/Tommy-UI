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

## Tommy UI v1.2

**Style Classic** pairs round icons with a new mountain-and-river wallpaper. Tommy Camera gives you a visible Modes button, front and rear photo and video capture, and a horizon indicator when you enable the composition grid. The level turns mint when the phone is within two degrees of horizontal. On an upgraded phone, open **Settings > About Tommy UI > River at dusk** to preview and apply the new wallpaper.

USB is the recommended update method. Original v1.003, v1.1 and v1.1.1 each have a direct signed OTA preview to v1.2 for the matching North American XT2413-2. Wireless installation or restart may fail. Back up your phone and adopted SD card. [Update instructions](guides/CLASSIC-UPDATE.md) / [v1.2 release](https://github.com/tommylikestech/Tommy-UI/releases/tag/v1.200)

Older releases are archived. The GitHub tags are v1.100 for v1.1, v1.101 for v1.1.1 and v1.200 for v1.2. The published OS uses Android development signing keys. The GitHub Pages descriptors have a separate maintainer signature.

## What you can do

- **Choose where apps live.** Tommy Storage can move eligible apps between phone storage and an adopted microSD card. The system APK installer offers a choice when multiple destinations are available. Adopting a card formats and encrypts it for that phone, so copy your files first.
- **Download APKs on the phone.** Tommy Browser saves direct download links to Downloads, where you can open an APK with Android’s installer.
- **Check for system updates.** Settings checks GitHub Pages at boot and on request. The preview offers a signed payload for your installed build. [Update guide](guides/CLASSIC-UPDATE.md)
- **Make yourself at home.** Tommy Home removes the persistent search bar. About phone shows the Motorola hardware model. About Tommy UI shows the OS version and has a constellation Easter egg if you tap the version seven times.

## Build and test status

The retail XT2413-2 has completed the v1.1 to v1.1.1 OTA, booted the expected system image and mounted adopted storage. Tommy UI v1.2 declares the **2025-08-05** Android security patch. Camera capture and the v1.2 OTA reboot still need testing on hardware.

For build commands, the original image checksum, and a map of the source tree, see the [build guide](guides/BUILD.md). Tommy UI additions use Apache License 2.0. Upstream components retain their own terms. [License](LICENSE) · [Third-party notices](NOTICE)
