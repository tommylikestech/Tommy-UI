# USE AT YOUR OWN RISK

> [!CAUTION]
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

[Downloads and release history](https://tommylikestech.github.io/Tommy-UI/) · [Build guide](guides/BUILD.md) · [Contributing](CONTRIBUTING.md)

> **Over-the-air updates aren’t working right now.** I’ll get to fixing them when I can. Until then the wireless updater APKs, the Update Zero test and the v1.004 trial are offline, and **Tommy UI v1.003 is the only supported build.**

## Start here

| What’s on your phone? | Where to go |
| --- | --- |
| **Tommy UI v1.003** | You’re on the current build. Wireless updates aren’t working right now, so there is nothing to install. |
| **Tommy UI v1.002** | The [Windows USB updater instructions](updater/FLASH-AND-STORAGE.txt) cover upgrading an unlocked XT2413-2 on slot A using a locally built kit. |
| **Motorola stock Android** | The [v1.003 release](https://github.com/tommylikestech/Tommy-UI/releases/tag/v1.003) provides source code for building the OS. |

## What you can do

- **Choose where apps live.** Tommy Storage can move eligible apps between phone storage and an adopted microSD card. The system APK installer offers a choice when multiple destinations are available. Adopting a card formats and encrypts it for that phone, so copy your files first.
- **Download APKs on the phone.** Tommy Browser saves direct download links to Downloads, where you can open an APK with Android’s installer.
- **Check for system updates.** The update page shows your installed build, Android version, release notes, and whether a release is a security or content update. It checks at boot and when you open the page. Wireless updates aren’t working right now, so the feed lists **v1.003** with nothing to install. [Live update feed](https://tommylikestech.github.io/Tommy-UI/version.txt)
- **Make yourself at home.** Tommy Home removes the persistent search bar. About phone shows the Motorola hardware model. About Tommy UI shows the OS version and has a constellation Easter egg if you tap the version seven times.

## Build and test status

The original v1.003 system image passed verification and was flashed on the XT2413-2. The build uses AOSP development keys and declares the **2025-08-05** Android security patch. On-device confirmation of the v1.003 build, corrected SD totals, browser APK downloads, and an end-to-end wireless OS upgrade remains pending.

For build commands, the original image checksum, and a map of the source tree, see the [build guide](guides/BUILD.md). Tommy UI additions use Apache License 2.0. Upstream components retain their own terms. [License](LICENSE) · [Third-party notices](NOTICE)
