# Contributing to Tommy UI

Use the manifest revision in `release.json`. The source recipe checks every affected file before updating the checkout and accepts files that already match the intended result.

For an added file or replacement artwork, edit the file under `overlay/` and update its result SHA-256 in `files.json`. For a change to existing AOSP code, edit a checkout of the pinned revision and regenerate the corresponding unified diff under `patches/`. Patch paths are relative to the AOSP root. Record the SHA-256 of the resulting file and keep its upstream digest. Run the source check against a clean checkout before building. Keep upstream notices with the code.

The PowerShell scripts under `art/` generate the wallpaper and boot frames into `art/assets/`. Copy an intentional asset change into its overlay path and update its digest. The checked-in asset bytes are the build inputs.

## Tests

Run these from the repository root. The USB updater test requires Python with Tk. The fastboot tests use a POSIX command shim on Linux. They simulate the device and do not write to a phone.

```bash
python3 tests/test_v1003_flash_helper.py
python3 tests/test_v1003_usb_updater.py
python3 tests/test_install_destinations.py /path/to/aosp/prebuilts/jdk/jdk21/linux-x86
```

The Android build and filesystem verification commands are in the [build guide](guides/BUILD.md). For changes affecting storage, record the card capacity, portable or adopted state, selected Settings volume, and displayed total and used space. Compare phone and card readings after switching volumes. Test an eligible app move in both directions and reopen the app after reboot. For browser changes, test a direct APK link, the saved file in Downloads, and the installer handoff. Share results without device serial numbers or personal files.
