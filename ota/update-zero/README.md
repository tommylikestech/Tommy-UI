# Update Zero · 1.0.03 Test

Development preflight for the XT2413-2 on `TommyUI.v1.003.dev.20261106`. The title is a test label. The installed operating system version stays at v1.003.

**On the phone:** install the test Settings APK, then open **Settings → About Tommy UI → Update Tommy UI → Run Update Zero download test**. This downloads the 448-byte metadata file from GitHub Releases and checks its SHA-256. The separate Phone and Messaging APKs add a launch warning. Settings asks for acknowledgment on the next regular startup and before starting an OS update.

**Native preflight on Windows:** connect the unlocked development phone with USB debugging, find its serial with `adb devices`, then run `./Verify-UpdateZero.ps1 -Serial YOUR_SERIAL`. This downloads the same signed metadata and asks the Android update engine to verify its signature and check the active system partition against the source-copy operation. “Payload is applicable” is the expected result. It uses the development build's existing `su` for temporary file staging.

**Install status:** the verified source system is 2,041,184,256 bytes with SHA-256 `58672f248a7491fa026a8343f845bd648d31eb9536dd9f2481b55745d1e7d76f`. On the test phone, boot control reported slot A bootable and slot B unbootable. The GSI's update partition list contains only system, while boot, vbmeta and vbmeta_system have different contents between slots. A successful reboot test requires a device-specific package that prepares a compatible inactive boot chain, dynamic partitions and verified-boot configuration. This prerelease provides metadata for verification only and leaves the normal OTA feed at v1.003.
