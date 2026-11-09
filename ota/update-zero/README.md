# Update Zero · 1.0.03 Test

**Status: withdrawn.** Over-the-air updates aren’t working right now and will be fixed when time allows. The Update Zero and v1.004 trial releases are offline and both feeds list v1.003 with nothing to install. The notes below are kept for maintainers. [What was withdrawn](../../WITHDRAWN.md)

Development preflight for the XT2413-2 on `TommyUI.v1.003.dev.20261106`. The title is a test label. The installed operating system version stays at v1.003.

**On the phone:** install the test Settings APK, then open **Settings → About Tommy UI → Update Tommy UI → Run Update Zero download test**. This downloads the 448-byte metadata file from GitHub Releases and checks its SHA-256. The separate Phone and Messaging APKs add a launch warning. Settings asks for acknowledgment on the next regular startup and before starting an OS update.

**Native preflight on Windows:** connect the unlocked development phone with USB debugging, find its serial with `adb devices`, then run `./Verify-UpdateZero.ps1 -Serial YOUR_SERIAL`. This downloads the same signed metadata and asks the Android update engine to verify its signature and check the active system partition against the source-copy operation. “Payload is applicable” is the expected result. It uses the development build's existing `su` for temporary file staging.

**Install status:** the verified source system is 2,041,184,256 bytes with SHA-256 `58672f248a7491fa026a8343f845bd648d31eb9536dd9f2481b55745d1e7d76f`. On the test phone, boot control reported slot A bootable and slot B unbootable. The GSI's update partition list contains only system, while boot, vbmeta and vbmeta_system have different contents between slots. A successful reboot test requires a device-specific package that prepares a compatible inactive boot chain, dynamic partitions and verified-boot configuration. This prerelease provides metadata for verification only and leaves the normal OTA feed at v1.003.

## Installable trial for the audited device

The separate v1.004 candidate uses numeric build `TommyUI.v1.004.dev.20261108` so the updater can distinguish it from v1.003. Its system image includes the Phone and Messaging warnings. The candidate is an incremental, signed Android payload. For fixed firmware partitions it contains source-copy operations and hashes. It copies firmware from the running phone into the inactive slot, including bootloaders and verified-boot partitions. The manufacturer's newer factory package supplies partition names only.

Android's update engine rejects a partial update with error 23 (manifest parse error), just after the manifest downloads, unless every partition in it carries a `version` timestamp. The builder stamps system and each firmware copy with the target's `ro.system.build.date.utc` and refuses to sign a manifest with an unversioned partition. `update_engine_client --verify` checks signatures and source hashes only, so it does not catch a missing version.

The opt-in **Check staged OTA trial** button uses a separate signed Pages feed. **Stage trial update** requests `SWITCH_SLOT_ON_REBOOT=0`. Staging writes slot B and may create snapshots. A successful stage still requires checking boot control, target partitions and snapshot status before a separate activation decision. Cold boot, fallback and snapshot merge require hardware validation.

For maintainers reproducing the exact audited configuration:

1. Use `capture_pairs.py --serial SERIAL inventory.txt` with the USB-connected phone on its original v1.003 slot A. Keep the inventory local.
2. Run `bash ota/update-zero/build_system.sh /path/to/aosp` in the source repository.
3. Run `python3 ota/update-zero/build_candidate.py AOSP FACTORY INVENTORY SOURCE_SYSTEM TARGET_SYSTEM PRIVATE_OUTPUT` on Linux. Use the original v1.003 image and the just-built target. The factory recipe is for XT2413-2 fogona_g retail revision U1TFS34.100-35-14-1-21. The source phone's vendor revision is U1TFS34.100-35-14-1-12.
4. Recheck the inventory immediately before enrollment. Verify the **new** `candidate.metadata` using the device's `update_engine_client --verify` and confirm unchanged, omitted partitions match across both slots. Enroll only the audited device. The normal stable feed remains independent.

## Diagnosing an already-staged R2 (private hardware test)

The R2 timestamp fix addresses manifest error 23. A completed staging attempt uses
`SWITCH_SLOT_ON_REBOOT=0`: engine result **52** means the payload was applied while the old slot stayed selected.
The old Settings client stored a per-build staging flag and disabled its Restart action indefinitely.
That flag alone cannot establish what is currently on the inactive slot. Both public feeds remain withdrawn.

For the original XT2413-2 test unit, use the local R2 metadata whose SHA-256 is
`026f47d7579541683dc4a911f6d32433627e625c6ac56e0ff8f6db6e2d784c51`.

1. With USB debugging connected, run `python ota/update-zero/audit_staged_r2.py --serial SERIAL candidate-r2.metadata` from the repository root. This checks the running build and slot, Android's snapshot and postinstall state, the target system hash, fixed firmware pairs and unchanged logical partition extents. Record the **first failing check** if it stops. A normal reboot while slot A is selected starts v1.003 again.
2. With the private Settings repair APK installed, repeat the command with `--authorize-ui` to prepare the finish action. This mode may map staged snapshots, makes a verified copy of the metadata available to Android, and grants a ticket for the current boot. It leaves boot selection unchanged.
3. Reopen **Settings → About Tommy UI → Update Tommy UI**. **Finish verified trial** requests a separate confirmation to have Android finish the update and select the audited target. **Restart into v1.004** becomes available when Android reports that a reboot is required. If either action fails, preserve its exact error and the update-engine log before attempting recovery.

Tests cover the audit and UI state transitions with simulated inputs. A successful target boot, fallback and snapshot merge still require testing on hardware. No public rollout is implied by this repair.
