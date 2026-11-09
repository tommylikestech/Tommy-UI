# Withdrawn releases

On **November 9, 2026 (UTC)** four prereleases were taken off the [Releases page](https://github.com/tommylikestech/Tommy-UI/releases). Their files can no longer be downloaded. This page records what was removed and exactly what each release contained.

All four were part of the over-the-air (wireless) update work. **Over-the-air updates aren’t working right now.** I’ll get to fixing them when I can. They were withdrawn so nobody damages a phone with unfinished update code. **Tommy UI v1.003 is the only supported build.**

| Release | Tag | Published (UTC) | What it was |
| --- | --- | --- | --- |
| [Update Zero · staged OTA trial (v1.004)](#update-zero--staged-ota-trial-v1004) | `v1.004` | 2026-11-09 03:08 | Experimental v1.003 → v1.004 wireless OS update for one audited test phone |
| [Update Zero · 1.0.03 Test](#update-zero--1003-test) | `update-zero-1.0.03` | 2026-11-09 02:40 | Download-and-verify test of the update path. It did not install an OS |
| [Wireless updater 2 · Settings menu fix](#wireless-updater-2--settings-menu-fix) | `updater-2` | 2026-11-09 02:25 | App update that added the update page to Settings on v1.003 |
| [Wireless updater · App downloads for v1.003](#wireless-updater--app-downloads-for-v1003) | `updater-1` | 2026-11-09 00:53 | First app prerelease of the update page |

## What changed and what didn’t

- The four releases above are hidden and their download links return “not found”. The maintainer kept private copies.
- The git tags are still in this repository, so the source code behind each withdrawn release can still be read.
- Both update feeds now list **v1.003** with nothing to install. No feed offers a download.
- The v1.003 source release was not removed. Its notes now carry the risk warning and this notice.

## Update Zero · staged OTA trial (v1.004)

Tag `v1.004`, built from commit `2d82a7b`.

An experimental incremental OS update from build `TommyUI.v1.003.dev.20261106` to `TommyUI.v1.004.dev.20261108`, tied to one audited XT2413-2. It carried a system update plus instructions to copy 22 firmware partitions, including bootloaders, into the phone’s inactive slot (slot B). It staged the update without switching the phone over to it. It was offered only through a separate opt-in trial feed.

**State when withdrawn:** Android rejected the first payload with error 23 before installing anything. The corrected payload (the `-r2` files) staged on the one test phone. The v1.004 system was never booted, and fallback and snapshot merge were never tested.

| File | What it was | Size (bytes) | SHA-256 |
| --- | --- | --- | --- |
| `Tommy-UI-fogona.bin` | First OTA payload. Rejected by Android with error 23 | 2,518,555 | `11a185df562293ef61d6ec68db7e94b7b60d8892ad3f627767eb8cdd6a876667` |
| `Tommy-UI-fogona-r2.bin` | Corrected OTA payload | 2,518,854 | `f2c643498f3d53be312c3a6a4c2383dfb1193306e41b7d1033368a2d78627f47` |
| `candidate.metadata` | Signed manifest of the first payload | 246,249 | `ddac5dbe950d3ab89278a903eb405e2e80e55baebf24077d7f384babf8dd8c18` |
| `candidate-r2.metadata` | Signed manifest of the corrected payload | 246,548 | `026f47d7579541683dc4a911f6d32433627e625c6ac56e0ff8f6db6e2d784c51` |
| `payload_properties.txt` | Hashes and sizes of the first payload | 153 | `6279b1d54de922d25f7ec56240361872b9334c1942e48653b0710e625a644ed3` |
| `payload_properties-r2.txt` | Hashes and sizes of the corrected payload | 153 | `d7ec429c464cd6f4ba26337a5cab691a4d1ade2c6b672d75bb873be336d6f35d` |
| `Tommy-Settings-trial.apk` | Settings build with the opt-in “Check staged OTA trial” button | 80,333,544 | `8e7f0cd98bc413488ab9dc1c66bf80012e21c1ab7dea8c47af2402e323626dad` |
| `SHA256SUMS.txt` | Checksums for the first set of files | 351 | `1d4cf3170600ae1efd7e0dc085aefa7056c7f863e1b1e146bcb77156f49613eb` |
| `SHA256SUMS-r2.txt` | Checksums for the corrected files | 269 | `91fffe4d5b8ee96adfa30b9dd10f3c872a7495c9c9340e8016c48fb1458b59ff` |

## Update Zero · 1.0.03 Test

Tag `update-zero-1.0.03`, built from commit `a857b14`.

A preflight test for phones on v1.003. A button in Settings downloaded a small metadata file and checked its checksum. It did not install an OS or switch slots. The Phone and Messaging test builds added a warning that calling and texting may not work.

| File | What it was | Size (bytes) | SHA-256 |
| --- | --- | --- | --- |
| `update-zero.metadata` | Small signed OTA metadata used by the download test | 448 | `a484ae0fa0ee0e8b2e3bbd1d78856157e49bd3454841f4b767a5a0429ae96ec4` |
| `Tommy-Settings.apk` | Settings test build with the “Run Update Zero download test” button | 80,333,544 | `62efe6f00c8566eb7d40af5cba3abbf9db2c0336adabafa40e0a990470d234d3` |
| `Tommy-Phone.apk` | Phone app test build with the launch warning | 12,131,140 | `4d8a7a82166b523fb2a58093258922691bf89a9f3ec638f071760f0fcad976a9` |
| `Tommy-Messaging.apk` | Messaging app test build with the launch warning | 11,043,081 | `eacbb7483ed07111af21e61c594de6aa25120b8e0cc535e5eef3bea7d617cd40` |
| `SHA256SUMS.txt` | Checksums for the files above | 340 | `1fa6f7056a062fde098f42c2cdc466104760bfe661fbec494164d5f8cbcfa878` |

## Wireless updater 2 · Settings menu fix

Tag `updater-2`, built from commit `23fe04c`.

Two app updates for phones on v1.003. They added **Settings → About Tommy UI → Update Tommy UI**, which checks the signed update feed. This release fixed the missing About Tommy UI row from Updater 1. It contained no OS update.

| File | What it was | Size (bytes) | SHA-256 |
| --- | --- | --- | --- |
| `Tommy-Settings.apk` | Settings with the update page and the menu fix | 80,325,352 | `d589cc3abadc369c637a0e14bba70b53df31996ae006ce81259c8b5124dad31a` |
| `Tommy-Storage.apk` | Tommy Storage app, identical to Updater 1 | 57,831 | `d7c88bf274aebda5004d5407e21d487768d3dd524e5c3256afab37ba32382df4` |
| `SHA256SUMS.txt` | Checksums for the files above | 169 | `de3a3cf49790d1e9ea6871758763a866ec576574efd7b1f3a8fbc2e3437c65e8` |

## Wireless updater · App downloads for v1.003

Tag `updater-1`, built from commit `ea6f9a1`.

The first app prerelease of the update page for phones on v1.003. It contained no OS update.

| File | What it was | Size (bytes) | SHA-256 |
| --- | --- | --- | --- |
| `Tommy-Settings.apk` | Settings with the update page | 80,325,352 | `883df3ea007dfb9d41de9c495e548491961752073c8ebd94cc3753d299c4e276` |
| `Tommy-Storage.apk` | Tommy Storage app | 57,831 | `d7c88bf274aebda5004d5407e21d487768d3dd524e5c3256afab37ba32382df4` |
| `SHA256SUMS.txt` | Checksums for the files above | 169 | `8d1bc844a991182c7386c44335f6f6af4a02bc0927c82c64929081c473fb7e6e` |

## If you already installed something from these

- **Updater 1, Updater 2 or the Update Zero test apps:** the update page reports v1.003 with nothing to install. The “Run Update Zero download test” button now fails because its test file is offline.
- **The v1.004 staged trial:** it needed a developer setting on the one audited test phone, so no other phone should have it staged.

To check a file you downloaded earlier, compare its SHA-256 with the tables above.
